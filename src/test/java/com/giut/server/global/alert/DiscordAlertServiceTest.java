package com.giut.server.global.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.HandlerMapping;

class DiscordAlertServiceTest {

    @Test
    void postsSanitizedAlertAndSuppressesRepeatedErrors() throws Exception {
        AtomicReference<String> receivedPayload = new AtomicReference<>();
        AtomicInteger receivedCount = new AtomicInteger();
        CountDownLatch received = new CountDownLatch(1);

        HttpServer mockDiscord = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockDiscord.createContext("/webhook/test", exchange -> {
            receivedPayload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            receivedCount.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            received.countDown();
        });
        mockDiscord.start();

        try {
            String webhookUrl = "http://127.0.0.1:" + mockDiscord.getAddress().getPort() + "/webhook/test";
            DiscordAlertService service = new DiscordAlertService(WebClient.create(), webhookUrl, "test");
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/123");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/users/{userId}");
            Exception exception = new IllegalStateException("must-not-leak-this-detail");

            service.notifyServerError(request, exception);
            assertThat(received.await(2, TimeUnit.SECONDS)).isTrue();

            service.notifyServerError(request, exception);

            assertThat(receivedCount).hasValue(1);
            assertThat(receivedPayload.get())
                    .contains("/api/users/{userId}", "IllegalStateException", "test")
                    .doesNotContain("must-not-leak-this-detail", "/api/users/123")
                    .contains("allowed_mentions", "parse");
        } finally {
            mockDiscord.stop(0);
        }
    }
}
