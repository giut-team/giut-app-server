package com.giut.server.global.alert;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

class SlowApiAlertServiceTest {
    private static final String WEBHOOK = "https://discord.com/api/webhooks/123456/FAKE_TOKEN";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T08:00:00Z"), ZoneOffset.UTC);
    private final AtomicLong nanos = new AtomicLong(-10_000_000_000L);
    private final List<String> payloads = new CopyOnWriteArrayList<>();

    @Test
    void warnsAtThresholdWithSanitizedPayloadAndNoMentions() throws Exception {
        var service = service(() -> Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build()));
        service.notifySlowRequest("GET", "/api/profile/{userId}", 200, 1_999_999_999L);
        assertThat(payloads).isEmpty();
        service.notifySlowRequest("GET", "/api/profile/{userId}", 200, 2_000_000_000L);
        awaitFinished(service);
        assertThat(payloads).hasSize(1);
        assertThat(payloads.getFirst()).contains("API 응답 지연", "/api/profile/{userId}", "2000ms",
                "2026-10-23T08:00:00Z", "allowed_mentions", "\"parse\":[]")
                .doesNotContain("FAKE_TOKEN");
    }

    @Test
    void cooldownSuppressesGloballyAndReportsCountOnNextQualifyingRequest() throws Exception {
        var service = service(() -> Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build()));
        warn(service);
        awaitFinished(service);
        service.notifySlowRequest("GET", "/api/other", 500, 3_000_000_000L);
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(299));
        warn(service);
        assertThat(payloads).hasSize(1);
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(1));
        warn(service);
        awaitFinished(service);
        assertThat(payloads).hasSize(2);
        assertThat(payloads.getLast()).contains("추가 느린 요청: 2건");
    }

    @Test
    void failedDeliveryIsNotRetriedInATightLoopAndCountIsRetained() throws Exception {
        var service = service(() -> Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                .body("private remote response").build()));
        warn(service);
        awaitFinished(service);
        warn(service);
        assertThat(payloads).hasSize(1);
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(301));
        warn(service);
        awaitFinished(service);
        assertThat(payloads.getLast()).contains("추가 느린 요청: 2건")
                .doesNotContain("private remote response");
    }

    @Test
    void asynchronousTransmissionDoesNotWaitAndOnlyOneSendCanBeInFlight() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var service = service(() -> Mono.fromCallable(() -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test gate timeout");
            return ClientResponse.create(HttpStatus.NO_CONTENT).build();
        }));
        try (var callers = Executors.newFixedThreadPool(8)) {
            // Returns even while the synthetic Discord response remains blocked.
            callers.submit(() -> warn(service)).get(2, TimeUnit.SECONDS);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            nanos.addAndGet(TimeUnit.MINUTES.toNanos(10));
            var tasks = java.util.stream.IntStream.range(0, 32)
                    .mapToObj(i -> callers.submit(() -> warn(service))).toList();
            for (var task : tasks) task.get(2, TimeUnit.SECONDS);
            assertThat(payloads).hasSize(1);
            release.countDown();
            awaitFinished(service);
            warn(service);
            awaitFinished(service);
            assertThat(payloads.getLast()).contains("추가 느린 요청: 32건");
        } finally {
            release.countDown();
        }
    }

    @Test
    void networkTimeoutFailsSafelyWithoutChangingCallerResult() throws Exception {
        var service = service(Mono::never);
        assertThatCode(() -> warn(service)).doesNotThrowAnyException();
        awaitFinished(service);
        assertThat(payloads).hasSize(1);
        warn(service);
        assertThat(payloads).hasSize(1);
    }

    @Test
    void malformedOrMissingWebhookAndDisabledModeNeverSend() {
        for (String webhook : List.of("", " ", "http://discord.com/api/webhooks/123/token",
                "https://evil.invalid/api/webhooks/123/token",
                WEBHOOK + "?secret=private", WEBHOOK + "#fragment",
                "https://discord.com@evil.invalid/api/webhooks/123/token")) {
            var service = new SlowApiAlertService(WebClient.create(), webhook, "dev", true,
                    2000, 300, nanos::get, CLOCK);
            assertThat(service.isActive()).isFalse();
            assertThatCode(() -> warn(service)).doesNotThrowAnyException();
        }
        var disabled = new SlowApiAlertService(WebClient.create(), WEBHOOK, "dev", false,
                2000, 300, nanos::get, CLOCK);
        assertThat(disabled.isActive()).isFalse();
        warn(disabled);
    }

    @Test
    void invalidConfigIsRejectedAndUnknownMetadataIsNotReflected() throws Exception {
        for (long threshold : new long[]{0, -1, 3_600_001}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new SlowApiAlertService(
                    WebClient.create(), WEBHOOK, "dev", true, threshold, 300, nanos::get, CLOCK));
        }
        for (long cooldown : new long[]{0, -1, 86_401}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new SlowApiAlertService(
                    WebClient.create(), WEBHOOK, "dev", true, 2000, cooldown, nanos::get, CLOCK));
        }
        var service = new SlowApiAlertService(client(() -> Mono.just(
                ClientResponse.create(HttpStatus.NO_CONTENT).build())), WEBHOOK, "@everyone\nsecret",
                true, 2000, 300, nanos::get, CLOCK);
        service.notifySlowRequest("GET\nPRIVATE", null, 0, 3_000_000_000L);
        awaitFinished(service);
        assertThat(payloads.getFirst()).contains("unknown", "OTHER", "(매핑 없음)", "미확정")
                .doesNotContain("PRIVATE", "@everyone", "secret");
    }

    @Test
    void nonSuccessIncludingRedirectsNeverCountsAsSuccess() throws Exception {
        for (int status : new int[]{302, 400, 500}) {
            payloads.clear();
            var service = service(() -> Mono.just(ClientResponse.create(
                    org.springframework.http.HttpStatusCode.valueOf(status))
                    .header("Location", "https://evil.invalid").body("SECRET_RESPONSE").build()));
            warn(service);
            awaitFinished(service);
            nanos.addAndGet(TimeUnit.MINUTES.toNanos(10));
            warn(service);
            awaitFinished(service);
            assertThat(payloads).hasSize(2);
            assertThat(payloads.getLast()).contains("추가 느린 요청: 1건")
                    .doesNotContain("SECRET_RESPONSE", "evil.invalid");
        }
    }

    @Test
    void slowWarningAndExistingServerErrorHaveIndependentCooldownBudgets() throws Exception {
        WebClient webClient = client(() -> Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build()));
        var slow = new SlowApiAlertService(webClient, WEBHOOK, "dev", true,
                2000, 300, nanos::get, CLOCK);
        var errors = new DiscordAlertService(webClient, WEBHOOK, "dev");
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/profile/5");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/profile/{userId}");

        warn(slow);
        awaitFinished(slow);
        errors.notifyServerError(request, new IllegalStateException("PRIVATE_EXCEPTION"));
        assertThat(payloads).hasSize(2);
        assertThat(payloads.getFirst()).contains("API 응답 지연");
        assertThat(payloads.getLast()).contains("서버 오류 알림").doesNotContain("PRIVATE_EXCEPTION");
        warn(slow);
        errors.notifyServerError(request, new IllegalStateException());
        assertThat(payloads).hasSize(2);
    }

    private SlowApiAlertService service(Supplier<Mono<ClientResponse>> response) {
        return new SlowApiAlertService(client(response), WEBHOOK, "dev", true,
                2000, 300, nanos::get, CLOCK);
    }

    private WebClient client(Supplier<Mono<ClientResponse>> response) {
        return WebClient.builder().exchangeFunction(request -> {
            var capture = new MockClientHttpRequest(request.method(), request.url());
            return request.writeTo(capture, ExchangeStrategies.withDefaults())
                    .then(Mono.defer(capture::getBodyAsString)).flatMap(body -> {
                        payloads.add(body);
                        return response.get();
                    });
        }).build();
    }

    private void warn(SlowApiAlertService service) {
        service.notifySlowRequest("GET", "/api/profile/{userId}", 200, 3_000_000_000L);
    }

    static void awaitFinished(SlowApiAlertService service) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (isInFlight(service) && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(isInFlight(service)).isFalse();
    }

    private static boolean isInFlight(SlowApiAlertService service) {
        synchronized (service) {
            return Boolean.TRUE.equals(ReflectionTestUtils.getField(service, "inFlight"));
        }
    }
}
