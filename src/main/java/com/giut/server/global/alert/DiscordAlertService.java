package com.giut.server.global.alert;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.HandlerMapping;

import reactor.core.publisher.Mono;

@Service
public class DiscordAlertService {

    private static final Logger logger = LoggerFactory.getLogger(DiscordAlertService.class);
    private static final long ALERT_COOLDOWN_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;
    private final String webhookUrl;
    private final String environment;
    private final AtomicLong lastAlertAtMillis = new AtomicLong();
    private final AtomicInteger suppressedAlertCount = new AtomicInteger();

    public DiscordAlertService(
            WebClient webClient,
            @Value("${DISCORD_ALERT_WEBHOOK_URL:}") String webhookUrl,
            @Value("${APP_ENVIRONMENT:unknown}") String environment) {
        this.webClient = webClient;
        this.webhookUrl = webhookUrl;
        this.environment = environment;
    }

    public void notifyServerError(HttpServletRequest request, Exception exception) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!claimAlertSlot(now)) {
            suppressedAlertCount.incrementAndGet();
            return;
        }

        int suppressedCount = suppressedAlertCount.getAndSet(0);
        String route = bestMatchingRoute(request);
        String content = "🚨 **기웃 서버 오류 알림**"
                + "\n환경: " + environment
                + "\n시각(UTC): " + Instant.now()
                + "\nHTTP: " + request.getMethod() + " " + route
                + "\n오류 종류: " + exception.getClass().getSimpleName()
                + (suppressedCount > 0 ? "\n최근 5분 동안의 추가 서버 오류 " + suppressedCount + "건은 묶어서 생략했습니다." : "");

        Map<String, Object> payload = Map.of(
                "content", content,
                "allowed_mentions", Map.of("parse", List.of()));

        try {
            webClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(REQUEST_TIMEOUT)
                    .doOnError(error -> logger.warn("Discord 서버 오류 알림 전송 실패 ({})",
                            error.getClass().getSimpleName()))
                    .onErrorResume(error -> Mono.empty())
                    .subscribe();
        } catch (RuntimeException error) {
            logger.warn("Discord 서버 오류 알림 요청을 시작하지 못했습니다 ({})",
                    error.getClass().getSimpleName());
        }
    }

    private boolean claimAlertSlot(long now) {
        while (true) {
            long previous = lastAlertAtMillis.get();
            if (now - previous < ALERT_COOLDOWN_MILLIS) {
                return false;
            }
            if (lastAlertAtMillis.compareAndSet(previous, now)) {
                return true;
            }
        }
    }

    private String bestMatchingRoute(HttpServletRequest request) {
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return route instanceof String pattern ? pattern : "(경로 확인 불가)";
    }
}
