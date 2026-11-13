package com.giut.server.competition.service;

import com.giut.server.competition.event.CompetitionExtractionFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * 추출 작업이 최종 실패로 확정된 뒤(commit 이후) 서버 로그와 Discord에 알린다.
 *
 * <p>로그는 항상 남기고, Discord는 best-effort다. 전달을 보장하지 않으며 관리자 실패 목록
 * ({@code GET /api/admin/competitions/extraction-failures})이 기준 기록이다.
 * AI 서버가 멈춰 실패가 몰릴 때 채널이 가득 차지 않도록 {@link #COOLDOWN}마다 한 번만 보내고,
 * 그 사이의 실패 건수는 다음 알림에 합쳐서 알린다.
 */
@Service
public class CompetitionExtractionFailureAlertService {
    /** 서버 로그에서 최종 실패만 검색할 때 쓰는 고정 접두어. 예: docker logs giut-dev | grep COMPETITION_EXTRACTION_FAILURE */
    public static final String FAILURE_LOG_PREFIX = "COMPETITION_EXTRACTION_FAILURE";
    static final Duration COOLDOWN = Duration.ofMinutes(1);
    private static final Logger logger = LoggerFactory.getLogger(CompetitionExtractionFailureAlertService.class);
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(3);
    private static final int MAX_LOG_VALUE_LENGTH = 300;
    private static final int DELIVERY_SLOTS = 4;

    private final WebClient webClient;
    private final URI webhook;
    private final String environment;
    private final boolean enabled;
    private final Clock clock;
    // 전달 중인 요청 수를 제한한다. 무한 대기열을 만들지 않는다.
    private final Semaphore deliverySlots = new Semaphore(DELIVERY_SLOTS);
    private Instant lastSentAt;
    private int suppressed;

    @Autowired
    public CompetitionExtractionFailureAlertService(
            WebClient webClient,
            @Value("${DISCORD_EXTRACTION_FAILURE_WEBHOOK_URL:${DISCORD_ALERT_WEBHOOK_URL:}}") String webhookUrl,
            @Value("${APP_ENVIRONMENT:unknown}") String environment,
            @Value("${DISCORD_EXTRACTION_FAILURE_ENABLED:true}") boolean enabled) {
        this(webClient, webhookUrl, environment, enabled, Clock.systemUTC());
    }

    CompetitionExtractionFailureAlertService(WebClient webClient, String webhookUrl, String environment,
                                             boolean enabled, Clock clock) {
        this.webClient = webClient;
        this.webhook = parseWebhook(webhookUrl);
        this.environment = environment != null && environment.matches("[A-Za-z0-9_.-]{1,32}") ? environment : "unknown";
        this.enabled = enabled;
        this.clock = clock;
        if (enabled && webhookUrl != null && !webhookUrl.isBlank() && webhook == null) {
            logger.warn("공모전 추출 실패 알림 URL 형식이 올바르지 않아 Discord 알림을 비활성화했습니다.");
        }
    }

    // 실패 확정 트랜잭션이 실제로 commit된 뒤에만 실행한다. 롤백된 실패를 알리거나,
    // 메시지가 가리키는 관리자 목록에 아직 없는 작업을 알리는 일을 막는다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFailed(CompetitionExtractionFailedEvent event) {
        logFailure(event);
        try {
            notifyDiscord(event);
        } catch (RuntimeException error) {
            // 알림 실패가 작업 처리나 다른 이벤트 처리를 막으면 안 된다.
            logger.warn("Discord 공모전 추출 실패 알림 처리 실패 (jobId={}, {})", event.jobId(),
                    error.getClass().getSimpleName());
        }
    }

    private void logFailure(CompetitionExtractionFailedEvent event) {
        logger.warn("{} jobId={} code={} attempts={} waitingUsers={} url={} message={} detail={}", FAILURE_LOG_PREFIX,
                event.jobId(), event.errorCode(), event.attemptCount(), event.waitingUsers(),
                sanitize(event.sourceUrl()), sanitize(event.errorMessage()), sanitize(event.errorDetail()));
    }

    private void notifyDiscord(CompetitionExtractionFailedEvent event) {
        if (!enabled || webhook == null) {
            return;
        }
        int omitted = claimSlot();
        if (omitted < 0) {
            return;
        }
        if (!deliverySlots.tryAcquire()) {
            logger.warn("Discord 공모전 추출 실패 알림 전송 여유가 없어 생략했습니다 (jobId={}).", event.jobId());
            return;
        }
        try {
            Map<String, Object> payload = Map.of("content", message(event, omitted),
                    "allowed_mentions", Map.of("parse", List.of()));
            Mono.defer(() -> webClient.post().uri(webhook).contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(payload).exchangeToMono(response -> {
                                boolean success = response.statusCode().is2xxSuccessful();
                                return response.releaseBody().then(success ? Mono.just(true)
                                        : Mono.error(new IllegalStateException("Notification delivery rejected")));
                            }))
                    .timeout(SEND_TIMEOUT)
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnError(error -> deliveryFailed(event.jobId()))
                    .onErrorResume(error -> Mono.empty())
                    .doFinally(signal -> deliverySlots.release())
                    .subscribe();
        } catch (RuntimeException error) {
            deliverySlots.release();
            deliveryFailed(event.jobId());
        }
    }

    /** 쿨다운 안이면 -1(보내지 않고 건수만 센다), 보낼 수 있으면 그동안 생략한 건수를 돌려주고 0으로 되돌린다. */
    private synchronized int claimSlot() {
        Instant now = clock.instant();
        if (lastSentAt != null && now.isBefore(lastSentAt.plus(COOLDOWN))) {
            suppressed++;
            return -1;
        }
        int omitted = suppressed;
        suppressed = 0;
        lastSentAt = now;
        return omitted;
    }

    private String message(CompetitionExtractionFailedEvent event, int omitted) {
        // 회원이 입력한 URL의 경로·쿼리와 내부 오류 상세는 보내지 않는다. 도메인과 작업 번호만 보내고 상세는 관리자 목록에서 본다.
        return "🚨 **기웃 공모전 URL 추출 최종 실패**"
                + "\n환경: " + environment
                + "\n작업 번호: " + event.jobId()
                + "\n오류 코드: " + safeCode(event.errorCode())
                + "\n시도 횟수: " + event.attemptCount()
                + "\n결과를 기다리는 회원: " + event.waitingUsers() + "명"
                + "\n공고 도메인: " + domain(event.sourceUrl())
                + "\n실패 시각(UTC): " + event.failedAt()
                + "\n관리자 확인: GET /api/admin/competitions/extraction-failures"
                + (omitted > 0 ? "\n직전 알림 이후 추가 실패 " + omitted + "건은 묶어서 생략했습니다. 목록에서 확인하세요." : "");
    }

    private void deliveryFailed(Long jobId) {
        // webhook, 원격 응답 본문, 예외 메시지는 남기지 않는다.
        logger.warn("Discord 공모전 추출 실패 알림 전송 실패 (jobId={}); 실패 기록은 관리자 목록에 유지됩니다.", jobId);
    }

    private static String safeCode(String code) {
        return code != null && code.matches("[A-Z_]{1,40}") ? code : "UNKNOWN";
    }

    private static String domain(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null && host.matches("[A-Za-z0-9.-]{1,100}") ? host : "(확인 불가)";
        } catch (RuntimeException error) {
            return "(확인 불가)";
        }
    }

    /** 외부 입력을 로그 한 줄에 안전하게 남기도록 줄바꿈을 지우고 길이를 제한한다. */
    private static String sanitize(String value) {
        if (value == null) return null;
        String singleLine = value.replaceAll("[\\r\\n\\t]+", " ");
        return singleLine.length() <= MAX_LOG_VALUE_LENGTH ? singleLine : singleLine.substring(0, MAX_LOG_VALUE_LENGTH) + "...";
    }

    private static URI parseWebhook(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            // 실제 discord.com webhook 경로만 허용한다. 변조된 설정값으로 다른 호스트에 요청하는 것을 막는다(SSRF 방지).
            return "https".equals(uri.getScheme()) && "discord.com".equals(uri.getRawAuthority())
                    && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && uri.getPath().matches("/api/webhooks/[0-9]+/[A-Za-z0-9_.-]+") ? uri : null;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}
