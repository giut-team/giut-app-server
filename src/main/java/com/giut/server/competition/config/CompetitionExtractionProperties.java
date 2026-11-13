package com.giut.server.competition.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 회원 URL 추출 작업자 설정. 기본값은 단일 서버·소규모 요청을 가정한 보수적인 값이며 실제 부하를 측정한 값이 아니다.
 * 근거와 대가는 docs/competition-extraction-jobs.md에 정리한다.
 *
 * @param workerEnabled            false면 이 서버는 접수·조회만 하고 작업을 실행하지 않는다.
 * @param concurrency              동시에 실행할 AI 호출 수(전용 스레드 수). 이보다 많이 선점하지 않는다.
 * @param pollInterval             대기 작업 선점 주기.
 * @param callTimeout              AI 호출 한 번의 제한 시간(약 1분 응답을 수용).
 * @param leaseDuration            RUNNING 임대 시간. callTimeout + 저장 여유보다 길어야 한다.
 * @param maxAttempts              선점 횟수 상한(일시 실패 재시도·임대 만료 회수 포함).
 * @param retryBaseDelay           일시 실패 재시도 첫 대기 시간(이후 2배씩).
 * @param retryMaxDelay            재시도 대기 상한.
 * @param pendingTimeout           접수 후 이 시간 안에 끝나지 않고 대기 중이면 최종 실패로 정리한다.
 * @param resultRetention          성공 작업(결과) 보관 기간. 지나면 삭제한다.
 * @param failureRetention         실패 작업 보관 기간. 관리자가 실패 목록에서 확인할 수 있는 기간이라 성공 결과보다 길게 둔다.
 * @param maxActivePerUser         사용자별 진행 중(PENDING/RUNNING) 작업 상한.
 * @param maxRequestsPerUserPerHour 사용자별 1시간 신규 작업 생성 상한.
 * @param maxActiveTotal           전체 진행 중 작업 상한(포화 시 신규 접수 503).
 * @param maintenanceBatchSize     회수·정리 1회 처리 행 수 상한.
 */
@ConfigurationProperties("app.ai-extraction.jobs")
public record CompetitionExtractionProperties(
        @DefaultValue("true") boolean workerEnabled,
        @DefaultValue("2") int concurrency,
        @DefaultValue("2s") Duration pollInterval,
        @DefaultValue("90s") Duration callTimeout,
        @DefaultValue("180s") Duration leaseDuration,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("30s") Duration retryBaseDelay,
        @DefaultValue("5m") Duration retryMaxDelay,
        @DefaultValue("15m") Duration pendingTimeout,
        @DefaultValue("24h") Duration resultRetention,
        @DefaultValue("30d") Duration failureRetention,
        @DefaultValue("3") int maxActivePerUser,
        @DefaultValue("20") int maxRequestsPerUserPerHour,
        @DefaultValue("50") int maxActiveTotal,
        @DefaultValue("100") int maintenanceBatchSize
) {
    /** 임대 만료 판정이 정상 실행 중인 호출을 가로채지 않도록 두는 최소 여유(결과 저장·GC·시계 오차). */
    public static final Duration MIN_LEASE_MARGIN = Duration.ofSeconds(30);

    public CompetitionExtractionProperties {
        require(concurrency >= 1 && concurrency <= 16, "concurrency must be between 1 and 16");
        require(positive(pollInterval), "poll-interval must be positive");
        require(positive(callTimeout), "call-timeout must be positive");
        require(leaseDuration != null && leaseDuration.compareTo(callTimeout.plus(MIN_LEASE_MARGIN)) >= 0,
                "lease-duration must be at least call-timeout + 30s");
        require(maxAttempts >= 1 && maxAttempts <= 10, "max-attempts must be between 1 and 10");
        require(positive(retryBaseDelay) && positive(retryMaxDelay) && retryMaxDelay.compareTo(retryBaseDelay) >= 0,
                "retry delays must be positive and max >= base");
        require(pendingTimeout != null && pendingTimeout.compareTo(leaseDuration) > 0,
                "pending-timeout must be longer than lease-duration");
        require(positive(resultRetention), "result-retention must be positive");
        require(positive(failureRetention), "failure-retention must be positive");
        require(maxActivePerUser >= 1, "max-active-per-user must be positive");
        require(maxRequestsPerUserPerHour >= 1, "max-requests-per-user-per-hour must be positive");
        require(maxActiveTotal >= 1, "max-active-total must be positive");
        require(maintenanceBatchSize >= 1 && maintenanceBatchSize <= 1000, "maintenance-batch-size must be 1..1000");
    }

    /** n번째 시도(1부터)가 일시 실패했을 때 다음 시도까지의 대기 시간: base * 2^(n-1), 상한 retryMaxDelay. */
    public Duration retryDelay(int attempt) {
        int exponent = Math.max(0, Math.min(attempt - 1, 20));
        Duration delay = retryBaseDelay.multipliedBy(1L << exponent);
        return delay.compareTo(retryMaxDelay) > 0 ? retryMaxDelay : delay;
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException("app.ai-extraction.jobs: " + message);
        }
    }
}
