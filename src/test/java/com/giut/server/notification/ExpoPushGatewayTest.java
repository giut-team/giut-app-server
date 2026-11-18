package com.giut.server.notification;

import com.giut.server.notification.config.PushProperties;
import com.giut.server.notification.push.ExpoPushGateway;
import com.giut.server.notification.push.PushGateway.PushMessage;
import com.giut.server.notification.push.PushGateway.PushReceipt;
import com.giut.server.notification.push.PushGateway.PushTicket;
import com.giut.server.notification.push.PushGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ExpoPushGatewayTest {
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void sendsExpoMessagesWithAccessTokenAndMapsTicketsInOrder() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        AtomicReference<String> sentBody = new AtomicReference<>();
        WebClient client = WebClient.builder().exchangeFunction(request -> {
            captured.set(request);
            sentBody.set(body(request));
            return json(HttpStatus.OK, """
                    {"data":[{"status":"ok","id":"ticket-1"},
                             {"status":"error","message":"not registered","details":{"error":"DeviceNotRegistered"}},
                             {"status":"error","message":"no details"}]}
                    """);
        }).build();

        List<PushTicket> tickets = gateway(client, "secret-token", "default").send(List.of(
                message("ExponentPushToken[a]", 3), message("ExponentPushToken[b]", null), message("ExponentPushToken[c]", 0)));

        assertThat(tickets).containsExactly(PushTicket.ok("ticket-1"),
                PushTicket.error("DeviceNotRegistered", "not registered"), PushTicket.error("EXPO_ERROR", "no details"));
        assertThat(captured.get().method()).isEqualTo(HttpMethod.POST);
        assertThat(captured.get().url()).isEqualTo(URI.create("https://exp.host/--/api/v2/push/send"));
        assertThat(captured.get().headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer secret-token");
        JsonNode body = objectMapper.readTree(sentBody.get());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(3);
        JsonNode first = body.get(0);
        assertThat(first.get("to").asString()).isEqualTo("ExponentPushToken[a]");
        assertThat(first.get("title").asString()).isEqualTo("제목");
        assertThat(first.get("data").get("notificationId").asLong()).isEqualTo(1L);
        assertThat(first.get("sound").asString()).isEqualTo("default");
        assertThat(first.get("priority").asString()).isEqualTo("high");
        assertThat(first.get("badge").asInt()).isEqualTo(3);
        assertThat(first.get("channelId").asString()).isEqualTo("default");
        assertThat(body.get(1).has("badge")).isFalse();
    }

    @Test
    void omitsOptionalHeadersAndFieldsWhenNotConfigured() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        AtomicReference<String> sentBody = new AtomicReference<>();
        WebClient client = WebClient.builder().exchangeFunction(request -> {
            captured.set(request);
            sentBody.set(body(request));
            return json(HttpStatus.OK, "{\"data\":[{\"status\":\"ok\",\"id\":\"t\"}]}");
        }).build();

        gateway(client, "", "").send(List.of(message("ExponentPushToken[a]", null)));

        assertThat(captured.get().headers().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
        assertThat(objectMapper.readTree(sentBody.get()).get(0).has("channelId")).isFalse();
    }

    @Test
    void classifiesRequestFailures() {
        assertThat(sendFailure(json(HttpStatus.TOO_MANY_REQUESTS, "{\"errors\":[{\"code\":\"TOO_MANY_REQUESTS\"}]}")))
                .satisfies(error -> {
                    assertThat(error.retryable()).isTrue();
                    assertThat(error.code()).isEqualTo("HTTP_429");
                    assertThat(error.getMessage()).contains("TOO_MANY_REQUESTS");
                });
        assertThat(sendFailure(json(HttpStatus.BAD_GATEWAY, "")).retryable()).isTrue();
        assertThat(sendFailure(json(HttpStatus.BAD_REQUEST, "{\"errors\":[{\"code\":\"PUSH_TOO_MANY_NOTIFICATIONS\"}]}")).retryable())
                .isFalse();
        assertThat(sendFailure(json(HttpStatus.UNAUTHORIZED, "{\"errors\":[{\"code\":\"UNAUTHORIZED\"}]}")).retryable()).isFalse();
        // 결과 수가 요청과 다르면 짝지을 수 없으므로 재시도한다.
        assertThat(sendFailure(json(HttpStatus.OK, "{\"data\":[]}")).code()).isEqualTo("INVALID_RESPONSE");
        assertThat(sendFailure(json(HttpStatus.OK, "not json")).code()).isEqualTo("INVALID_RESPONSE");
        assertThat(sendFailure(Mono.error(new WebClientRequestException(new java.io.IOException("reset"),
                HttpMethod.POST, URI.create("https://exp.host"), new HttpHeaders()))))
                .satisfies(error -> {
                    assertThat(error.retryable()).isTrue();
                    assertThat(error.code()).isEqualTo("NETWORK_ERROR");
                });
        assertThat(sendFailure(Mono.never())).satisfies(error -> assertThat(error.code()).isEqualTo("NETWORK_ERROR"));
    }

    @Test
    void readsReceiptsAndLeavesMissingIdsOut() {
        AtomicReference<String> sentBody = new AtomicReference<>();
        WebClient client = WebClient.builder().exchangeFunction(request -> {
            assertThat(request.url()).isEqualTo(URI.create("https://exp.host/--/api/v2/push/getReceipts"));
            sentBody.set(body(request));
            return json(HttpStatus.OK, """
                    {"data":{"t1":{"status":"ok"},
                             "t2":{"status":"error","message":"rate","details":{"error":"MessageRateExceeded"}}}}
                    """);
        }).build();

        Map<String, PushReceipt> receipts = gateway(client, "", "").receipts(List.of("t1", "t2", "t3"));

        assertThat(receipts).containsOnly(Map.entry("t1", PushReceipt.accepted()),
                Map.entry("t2", PushReceipt.error("MessageRateExceeded", "rate")));
        assertThat(objectMapper.readTree(sentBody.get()).get("ids").size()).isEqualTo(3);
    }

    private PushGatewayException sendFailure(Mono<ClientResponse> response) {
        WebClient client = WebClient.builder().exchangeFunction(request -> response).build();
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> gateway(client, "", "").send(List.of(message("ExponentPushToken[a]", null))));
        assertThat(thrown).isInstanceOf(PushGatewayException.class);
        return (PushGatewayException) thrown;
    }

    private ExpoPushGateway gateway(WebClient client, String accessToken, String channelId) {
        PushProperties properties = new PushProperties(true, URI.create("https://exp.host/--/api/v2/push/send"),
                URI.create("https://exp.host/--/api/v2/push/getReceipts"), accessToken, channelId,
                Duration.ofSeconds(2), 100, Duration.ofMillis(300), Duration.ofSeconds(60), 5, Duration.ofSeconds(30),
                Duration.ofMinutes(10), Duration.ofHours(6), Duration.ofMinutes(15), Duration.ofHours(24), 300,
                Duration.ofDays(30), Duration.ofDays(270), 100);
        return new ExpoPushGateway(client, objectMapper, properties);
    }

    private static PushMessage message(String to, Integer badge) {
        return new PushMessage(to, "제목", "본문", Map.of("notificationId", 1L), badge);
    }

    private static Mono<ClientResponse> json(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status).header("Content-Type", "application/json").body(body).build());
    }

    private static String body(ClientRequest request) {
        MockClientHttpRequest mock = new MockClientHttpRequest(request.method(), request.url());
        request.body().insert(mock, new org.springframework.web.reactive.function.BodyInserter.Context() {
            @Override
            public List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters() {
                return org.springframework.web.reactive.function.client.ExchangeStrategies.withDefaults().messageWriters();
            }

            @Override
            public java.util.Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() {
                return java.util.Optional.empty();
            }

            @Override
            public Map<String, Object> hints() {
                return Map.of();
            }
        }).block();
        return mock.getBodyAsString().block();
    }
}
