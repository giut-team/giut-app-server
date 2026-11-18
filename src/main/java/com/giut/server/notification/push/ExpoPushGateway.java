package com.giut.server.notification.push;

import com.giut.server.notification.config.PushProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Expo Push API(https://docs.expo.dev/push-notifications/sending-notifications/) 구현.
 * Expo가 FCM(Android)·APNs(iOS)로 전달하므로 서버는 Firebase·Apple 자격 증명을 직접 갖지 않는다
 * (자격 증명은 EAS 프로젝트에 등록한다).
 *
 * <p>네트워크 오류·시간 초과·429·5xx는 재시도 가능, 그 밖의 4xx(형식 오류·401 access token 문제 등)는 재시도해도
 * 해결되지 않으므로 재시도하지 않는다(Expo 권장).
 */
@Component
public class ExpoPushGateway implements PushGateway {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final PushProperties properties;

    public ExpoPushGateway(WebClient webClient, ObjectMapper objectMapper, PushProperties properties) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<PushTicket> send(List<PushMessage> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> body = messages.stream().map(this::toExpoMessage).toList();
        JsonNode data = post(properties.expoSendUrl(), body).path("data");
        if (!data.isArray() || data.size() != messages.size()) {
            // 결과와 메시지를 짝지을 수 없다. Expo가 받았을 수도 있지만 어느 것인지 모르므로 재시도한다(중복 가능).
            throw new PushGatewayException("INVALID_RESPONSE", "Expo 발송 응답의 결과 수가 요청과 다릅니다.", true);
        }
        List<PushTicket> tickets = new ArrayList<>(data.size());
        for (JsonNode ticket : data) {
            if ("ok".equals(ticket.path("status").asString(null)) && ticket.hasNonNull("id")) {
                tickets.add(PushTicket.ok(ticket.get("id").asString()));
            } else {
                tickets.add(PushTicket.error(errorCode(ticket), ticket.path("message").asString(null)));
            }
        }
        return tickets;
    }

    @Override
    public Map<String, PushReceipt> receipts(Collection<String> ticketIds) {
        if (ticketIds.isEmpty()) {
            return Map.of();
        }
        JsonNode data = post(properties.expoReceiptsUrl(), Map.of("ids", List.copyOf(ticketIds))).path("data");
        if (!data.isObject()) {
            throw new PushGatewayException("INVALID_RESPONSE", "Expo 영수증 응답 형식이 올바르지 않습니다.", true);
        }
        Map<String, PushReceipt> receipts = new HashMap<>();
        for (Map.Entry<String, JsonNode> entry : data.properties()) {
            JsonNode receipt = entry.getValue();
            receipts.put(entry.getKey(), "ok".equals(receipt.path("status").asString(null))
                    ? PushReceipt.accepted()
                    : PushReceipt.error(errorCode(receipt), receipt.path("message").asString(null)));
        }
        return receipts;
    }

    private Map<String, Object> toExpoMessage(PushMessage message) {
        Map<String, Object> expo = new LinkedHashMap<>();
        expo.put("to", message.to());
        expo.put("title", message.title());
        expo.put("body", message.body());
        expo.put("data", message.data());
        expo.put("sound", "default");
        // Android 기본(normal)은 절전 모드에서 늦게 도착할 수 있다. 사용자에게 보이는 알림이므로 high로 보낸다.
        expo.put("priority", "high");
        if (message.badge() != null) {
            expo.put("badge", message.badge());
        }
        if (!properties.androidChannelId().isEmpty()) {
            expo.put("channelId", properties.androidChannelId());
        }
        return expo;
    }

    private JsonNode post(URI uri, Object body) {
        Raw raw;
        try {
            raw = webClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (!properties.expoAccessToken().isEmpty()) {
                            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + properties.expoAccessToken());
                        }
                    })
                    .bodyValue(body)
                    .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("")
                            .map(text -> new Raw(response.statusCode(), text)))
                    .timeout(properties.requestTimeout())
                    .block();
        } catch (RuntimeException exception) {
            throw new PushGatewayException("NETWORK_ERROR", "Expo 요청 실패: " + exception.getClass().getSimpleName(),
                    true, exception);
        }
        if (raw == null) {
            throw new PushGatewayException("NETWORK_ERROR", "Expo 응답이 비어 있습니다.", true);
        }
        int status = raw.status().value();
        if (!raw.status().is2xxSuccessful()) {
            boolean retryable = status == 429 || raw.status().is5xxServerError();
            throw new PushGatewayException("HTTP_" + status, "Expo 요청 실패(HTTP " + status + "): " + requestError(raw.body()),
                    retryable);
        }
        try {
            return objectMapper.readTree(raw.body());
        } catch (RuntimeException exception) {
            throw new PushGatewayException("INVALID_RESPONSE", "Expo 응답을 해석하지 못했습니다.", true, exception);
        }
    }

    private String requestError(String body) {
        try {
            JsonNode first = objectMapper.readTree(body).path("errors").path(0);
            return first.path("code").asString("UNKNOWN");
        } catch (RuntimeException exception) {
            return "UNKNOWN";
        }
    }

    private static String errorCode(JsonNode result) {
        String error = result.path("details").path("error").asString(null);
        return error == null || error.isBlank() ? "EXPO_ERROR" : error;
    }

    private record Raw(HttpStatusCode status, String body) {
    }
}
