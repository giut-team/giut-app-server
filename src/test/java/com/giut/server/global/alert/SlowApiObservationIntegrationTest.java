package com.giut.server.global.alert;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real embedded servlet container + Boot's production observation auto-registration.
 * No component scan, application.properties, datasource, JWT secrets, AWS, or real Discord.
 * Probe endpoints/filter are test-only and never part of the application JAR.
 */
class SlowApiObservationIntegrationTest {
    private static ServletWebServerApplicationContext context;
    private static ProbeState state;
    private static SlowApiAlertService service;
    private static int port;
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();

    @BeforeAll
    static void startServer() {
        var app = new SpringApplication(ProbeApplication.class);
        app.setWebApplicationType(WebApplicationType.SERVLET);
        context = (ServletWebServerApplicationContext) app.run(
                "--spring.config.location=optional:classpath:/slow-api-isolated-test.properties",
                "--server.address=127.0.0.1", "--server.port=0", "--spring.main.banner-mode=off",
                "--logging.level.root=ERROR", "--spring.jmx.enabled=false",
                "--springdoc.api-docs.enabled=false", "--springdoc.swagger-ui.enabled=false",
                "--management.endpoints.web.exposure.include=health");
        state = context.getBean(ProbeState.class);
        service = context.getBean(SlowApiAlertService.class);
        port = context.getWebServer().getPort();
    }

    @AfterAll
    static void stopServer() {
        if (context != null) context.close();
        if (state != null) state.workers.close();
    }

    @BeforeEach
    void reset() throws Exception {
        SlowApiAlertServiceTest.awaitFinished(service);
        state.payloads.clear();
        state.clock.addAndGet(TimeUnit.MINUTES.toNanos(10));
        state.asyncEntered = new CountDownLatch(1);
        state.asyncRelease = new CountDownLatch(1);
    }

    @Test
    void automaticallyObservesSyncRequestWithoutChangingResponseOrLeakingIdQueryHeader() throws Exception {
        var response = call("/api/probe/sync/PRIVATE-ID?email=SECRET_EMAIL");
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("original-body");
        String payload = awaitPayload();
        assertThat(payload).contains("/api/probe/sync/{id}", "201", "서버 처리 시간")
                .doesNotContain("PRIVATE-ID", "SECRET_EMAIL", "SECRET_HEADER", "FAKE_TOKEN");
    }

    @Test
    void includesSerializationAfterControllerReturn() throws Exception {
        var response = call("/api/probe/serialization");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("serialized-body");
        assertThat(awaitPayload()).contains("/api/probe/serialization");
    }

    @Test
    void includesAsyncWorkAndDoesNotSendAtInitialDispatchReturn() throws Exception {
        var pending = client.sendAsync(request("/api/probe/async/PRIVATE-ID"),
                HttpResponse.BodyHandlers.ofString());
        try {
            assertThat(state.asyncEntered.await(3, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);
            assertThat(state.payloads).isEmpty();
        } finally {
            state.asyncRelease.countDown();
        }
        var response = pending.get(5, TimeUnit.SECONDS);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("async-body");
        assertThat(awaitPayload()).contains("/api/probe/async/{id}").doesNotContain("PRIVATE-ID");
        SlowApiAlertServiceTest.awaitFinished(service);
        assertThat(state.payloads).hasSize(1);
    }

    @Test
    void asyncTimeoutRemains503AndProducesOneWarning() throws Exception {
        assertThat(call("/api/probe/timeout").statusCode()).isEqualTo(503);
        assertThat(awaitPayload()).contains("API 응답 지연");
        SlowApiAlertServiceTest.awaitFinished(service);
        assertThat(state.payloads).hasSize(1);
    }

    @Test
    void includesEarlierFilterTimeAndKeepsDenialResponse() throws Exception {
        var response = call("/api/probe/filter-denied");
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).isEqualTo("denied-body");
        assertThat(awaitPayload()).contains("(매핑 없음)", "403");
    }

    @Test
    void serverFailureStillReturns500WithoutLeakingExceptionDetails() throws Exception {
        assertThat(call("/api/probe/failure").statusCode()).isEqualTo(500);
        assertThat(awaitPayload()).contains("500").doesNotContain("PRIVATE_EXCEPTION_DETAIL");
    }

    @Test
    void sseAndNonApiTrafficDoNotProduceLatencyAlerts() throws Exception {
        assertThat(call("/api/probe/events").statusCode()).isEqualTo(200);
        assertThat(call("/warmup").statusCode()).isEqualTo(200);
        assertThat(state.payloads).isEmpty();
    }

    private static HttpResponse<String> call(String path) throws Exception {
        return client.send(request(path), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(6)).header("Authorization", "Bearer SECRET_HEADER")
                .GET().build();
    }

    private String awaitPayload() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (state.payloads.isEmpty() && System.nanoTime() < deadline) Thread.sleep(5);
        assertThat(state.payloads).hasSize(1);
        SlowApiAlertServiceTest.awaitFinished(service);
        return state.payloads.getFirst();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"
    })
    @Import(SlowApiObservationHandler.class)
    static class ProbeApplication {
        @Bean ProbeState state() { return new ProbeState(); }

        @Bean
        SlowApiAlertService alerts(ProbeState state) {
            WebClient webClient = WebClient.builder().exchangeFunction(request -> {
                var capture = new MockClientHttpRequest(request.method(), request.url());
                return request.writeTo(capture, ExchangeStrategies.withDefaults())
                        .then(Mono.defer(capture::getBodyAsString)).map(body -> {
                            state.payloads.add(body);
                            return ClientResponse.create(HttpStatus.NO_CONTENT).build();
                        });
            }).build();
            return new SlowApiAlertService(webClient,
                    "https://discord.com/api/webhooks/123456/FAKE_TOKEN",
                    "test", true, 50, 300, state.clock::get, Clock.systemUTC());
        }

        @Bean ProbeController probe(ProbeState state) { return new ProbeController(state); }

        @Bean
        FilterRegistrationBean<Filter> slowEarlyFilter() {
            Filter filter = (request, response, chain) -> {
                if (((HttpServletRequest) request).getRequestURI().equals("/api/probe/filter-denied")) {
                    pause();
                    ((HttpServletResponse) response).setStatus(403);
                    response.getWriter().write("denied-body");
                    return;
                }
                chain.doFilter(request, response);
            };
            var bean = new FilterRegistrationBean<>(filter);
            bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
            bean.setAsyncSupported(true);
            return bean;
        }
    }

    static class ProbeState {
        final CopyOnWriteArrayList<String> payloads = new CopyOnWriteArrayList<>();
        final AtomicLong clock = new AtomicLong();
        final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        volatile CountDownLatch asyncEntered = new CountDownLatch(1);
        volatile CountDownLatch asyncRelease = new CountDownLatch(1);
    }

    @RestController
    @TestComponent
    static class ProbeController {
        final ProbeState state;
        ProbeController(ProbeState state) { this.state = state; }

        @GetMapping("/api/probe/sync/{id}")
        ResponseEntity<Map<String, String>> sync(@PathVariable String id) {
            pause();
            return ResponseEntity.status(201).body(Map.of("value", "original-body"));
        }

        @GetMapping("/api/probe/serialization")
        SlowBody serialization() { return new SlowBody(); }

        @GetMapping("/api/probe/async/{id}")
        Callable<Map<String, String>> async(@PathVariable String id) {
            return () -> {
                state.asyncEntered.countDown();
                if (!state.asyncRelease.await(4, TimeUnit.SECONDS)) throw new IllegalStateException("test gate timeout");
                return Map.of("value", "async-body");
            };
        }

        @GetMapping("/api/probe/timeout")
        DeferredResult<String> timeout() { return new DeferredResult<>(100L); }

        @GetMapping("/api/probe/failure")
        String failure() {
            pause();
            throw new IllegalStateException("PRIVATE_EXCEPTION_DETAIL");
        }

        @GetMapping(value = "/api/probe/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter events() {
            var emitter = new SseEmitter(2000L);
            state.workers.submit(() -> {
                pause();
                emitter.complete();
            });
            return emitter;
        }

        @GetMapping("/warmup")
        Map<String, String> warmup() {
            pause();
            return Map.of("value", "warm");
        }
    }

    static class SlowBody {
        public String getValue() {
            pause();
            return "serialized-body";
        }
    }

    private static void pause() {
        try { Thread.sleep(100); }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted");
        }
    }
}
