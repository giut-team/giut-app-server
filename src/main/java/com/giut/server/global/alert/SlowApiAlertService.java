package com.giut.server.global.alert;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Best-effort, process-local latency warnings, independent of the 500-error alert budget.
 * No request object is retained by the asynchronous sender.
 */
@Service
public class SlowApiAlertService {
    private static final Logger logger = LoggerFactory.getLogger(SlowApiAlertService.class);
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(3);
    private final WebClient webClient;
    private final URI webhook;
    private final String environment;
    private final boolean enabled;
    private final long thresholdNanos;
    private final long thresholdMillis;
    private final long cooldownNanos;
    private final LongSupplier nanoTime;
    private final Clock clock;
    // Guarded by this. No route-keyed map or unbounded pending notification queue.
    private Long lastAttemptNanos;
    private boolean inFlight;
    private long suppressedCount;

    @Autowired
    public SlowApiAlertService(
            WebClient webClient,
            @Value("${DISCORD_SLOW_API_WEBHOOK_URL:${DISCORD_ALERT_WEBHOOK_URL:}}") String webhookUrl,
            @Value("${APP_ENVIRONMENT:unknown}") String environment,
            @Value("${DISCORD_SLOW_API_ENABLED:true}") boolean enabled,
            @Value("${DISCORD_SLOW_API_THRESHOLD_MS:2000}") long thresholdMillis,
            @Value("${DISCORD_SLOW_API_COOLDOWN_SECONDS:300}") long cooldownSeconds) {
        this(webClient, webhookUrl, environment, enabled, thresholdMillis, cooldownSeconds,
                System::nanoTime, Clock.systemUTC());
    }

    SlowApiAlertService(WebClient webClient, String webhookUrl, String environment,
                        boolean enabled, long thresholdMillis, long cooldownSeconds,
                        LongSupplier nanoTime, Clock clock) {
        if (thresholdMillis < 1 || thresholdMillis > 3_600_000
                || cooldownSeconds < 1 || cooldownSeconds > 86_400) {
            throw new IllegalArgumentException("Slow API threshold must be 1..3600000ms; cooldown 1..86400s");
        }
        this.webClient = webClient;
        this.webhook = parseWebhook(webhookUrl);
        this.environment = environment != null && environment.matches("[A-Za-z0-9_.-]{1,32}")
                ? environment : "unknown";
        this.enabled = enabled;
        this.thresholdMillis = thresholdMillis;
        this.thresholdNanos = TimeUnit.MILLISECONDS.toNanos(thresholdMillis);
        this.cooldownNanos = TimeUnit.SECONDS.toNanos(cooldownSeconds);
        this.nanoTime = nanoTime;
        this.clock = clock;
        if (enabled && webhookUrl != null && !webhookUrl.isBlank() && webhook == null) {
            logger.warn("느린 API 알림 URL 형식이 올바르지 않아 해당 알림을 비활성화했습니다.");
        }
    }

    public boolean isActive() {
        return enabled && webhook != null;
    }

    public void notifySlowRequest(String method, String route, int status, long elapsedNanos) {
        if (!isActive() || elapsedNanos < thresholdNanos) {
            return;
        }
        Long omitted = claimSlot(nanoTime.getAsLong());
        if (omitted == null) {
            return;
        }
        try {
            String safeMethod = List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE")
                    .contains(method) ? method : "OTHER";
            String safeRoute = safeRoute(route);
            String statusLabel = status >= 100 && status <= 599 ? Integer.toString(status) : "미확정";
            String content = "🐢 **기웃 API 응답 지연 알림**"
                    + "\n환경: " + environment
                    + "\n관측 시각(UTC): " + Instant.now(clock)
                    + "\nHTTP: " + safeMethod + " " + safeRoute
                    + "\n응답 상태(관측 시점): " + statusLabel
                    + "\n서버 처리 시간: " + TimeUnit.NANOSECONDS.toMillis(elapsedNanos) + "ms"
                    + " (기준: " + thresholdMillis + "ms)"
                    + (omitted > 0 ? "\n전송 제한·실패로 생략된 추가 느린 요청: " + omitted + "건" : "")
                    + "\nSQL·외부 API·커넥션 대기 등 원인은 별도 확인이 필요합니다.";
            Map<String, Object> payload = Map.of("content", content,
                    "allowed_mentions", Map.of("parse", List.of()));
            webClient.post().uri(webhook).contentType(MediaType.APPLICATION_JSON).bodyValue(payload)
                    .exchangeToMono(response -> {
                        boolean success = response.statusCode().is2xxSuccessful();
                        return response.releaseBody().then(success ? Mono.just(true)
                                : Mono.error(new IllegalStateException("Notification delivery rejected")));
                    })
                    .timeout(SEND_TIMEOUT)
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnError(error -> deliveryFailed(omitted))
                    .onErrorResume(error -> Mono.empty())
                    .doFinally(signal -> releaseSlot())
                    .subscribe();
        } catch (RuntimeException error) {
            deliveryFailed(omitted);
            releaseSlot();
        }
    }

    private synchronized Long claimSlot(long now) {
        if (inFlight || (lastAttemptNanos != null && now - lastAttemptNanos < cooldownNanos)) {
            suppressedCount = saturatedAdd(suppressedCount, 1);
            return null;
        }
        inFlight = true;
        lastAttemptNanos = now;
        long omitted = suppressedCount;
        suppressedCount = 0;
        return omitted;
    }

    private synchronized void deliveryFailed(long omitted) {
        suppressedCount = saturatedAdd(suppressedCount, saturatedAdd(omitted, 1));
        // Neither exception message/stack nor remote body/URL is logged.
        logger.warn("Discord 느린 API 알림 전송 실패; 전송 제한을 유지합니다.");
    }

    private synchronized void releaseSlot() {
        inFlight = false;
    }

    private static long saturatedAdd(long value, long additional) {
        return value > Long.MAX_VALUE - additional ? Long.MAX_VALUE : value + additional;
    }

    private static String safeRoute(String route) {
        // Caller supplies a framework route pattern, never a request URI/query string.
        if (route == null || route.length() > 256
                || !(route.equals("/api") || route.startsWith("/api/"))
                || route.chars().anyMatch(c -> c < 32 || c == 127)) {
            return "(매핑 없음)";
        }
        return route;
    }

    private static URI parseWebhook(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            return "https".equals(uri.getScheme()) && "discord.com".equals(uri.getRawAuthority())
                    && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && uri.getPath().matches("/api/webhooks/[0-9]+/[A-Za-z0-9_.-]+") ? uri : null;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}
