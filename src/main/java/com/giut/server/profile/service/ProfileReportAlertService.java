package com.giut.server.profile.service;

import com.giut.server.profile.entity.ProfileReport;
import com.giut.server.profile.event.ProfileReportCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Best-effort notification after the report transaction commits.
 * No durable delivery guarantee: the administrator report list remains the source of truth.
 */
@Service
public class ProfileReportAlertService {
    private static final Logger logger = LoggerFactory.getLogger(ProfileReportAlertService.class);
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;
    private final URI webhook;
    private final String environment;
    private final boolean enabled;
    // Bounded outstanding work instead of an unbounded queue or the error-alert cooldown.
    private final Semaphore deliverySlots = new Semaphore(8);

    public ProfileReportAlertService(
            WebClient webClient,
            @Value("${DISCORD_PROFILE_REPORT_WEBHOOK_URL:${DISCORD_ALERT_WEBHOOK_URL:}}") String webhookUrl,
            @Value("${APP_ENVIRONMENT:unknown}") String environment,
            @Value("${DISCORD_PROFILE_REPORT_ENABLED:true}") boolean enabled) {
        this.webClient = webClient;
        this.webhook = parseWebhook(webhookUrl);
        this.environment = environment != null && environment.matches("[A-Za-z0-9_.-]{1,32}")
                ? environment : "unknown";
        this.enabled = enabled;
        if (enabled && webhookUrl != null && !webhookUrl.isBlank() && webhook == null) {
            logger.warn("프로필 신고 알림 URL 형식이 올바르지 않아 해당 알림을 비활성화했습니다.");
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportCreated(ProfileReportCreatedEvent event) {
        if (!enabled || webhook == null) {
            return;
        }
        if (!deliverySlots.tryAcquire()) {
            logger.warn("Discord 프로필 신고 알림 전송 여유가 없어 생략했습니다 (reportId={}).", event.reportId());
            return;
        }
        try {
            String content = "📨 **기웃 프로필 신고 접수 알림**"
                    + "\n환경: " + environment
                    + "\n신고 번호: " + event.reportId()
                    + "\n접수 시각(UTC): " + event.createdAt()
                    + "\n신고 사유: " + reasonLabel(event.reason())
                    + "\n상태: PENDING"
                    + "\n관리자 상세 조회: GET /api/admin/profile-reports/" + event.reportId()
                    + "\n신고 내용과 사용자 정보는 관리자 화면에서 확인하세요.";
            Map<String, Object> payload = Map.of("content", content,
                    "allowed_mentions", Map.of("parse", List.of()));

            Mono.defer(() -> webClient.post().uri(webhook).contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(payload).exchangeToMono(response -> {
                                boolean success = response.statusCode().is2xxSuccessful();
                                return response.releaseBody().then(success ? Mono.just(true)
                                        : Mono.error(new IllegalStateException("Notification delivery rejected")));
                            }))
                    .timeout(SEND_TIMEOUT)
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnError(error -> deliveryFailed(event.reportId()))
                    .onErrorResume(error -> Mono.empty())
                    .doFinally(signal -> deliverySlots.release())
                    .subscribe();
        } catch (RuntimeException error) {
            deliverySlots.release();
            deliveryFailed(event.reportId());
        }
    }

    private void deliveryFailed(Long reportId) {
        // Do not log the webhook, remote body, exception message, or user content.
        logger.warn("Discord 프로필 신고 알림 전송 실패 (reportId={}); 접수된 신고는 유지됩니다.", reportId);
    }

    private static String reasonLabel(ProfileReport.Reason reason) {
        return switch (reason) {
            case SPAM_ADVERTISING -> "스팸·광고";
            case FALSE_INFORMATION_IMPERSONATION -> "허위 정보·사칭";
            case INAPPROPRIATE_BEHAVIOR -> "부적절한 행동";
            case OTHER -> "기타";
        };
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
