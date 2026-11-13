package com.giut.server.competition.entity;

import com.giut.server.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 회원이 요청한 공모전 URL AI 추출 작업 한 건.
 *
 * <p>상태 전이는 {@code CompetitionExtractionJobStore}의 짧은 트랜잭션 안에서만 일어난다.
 * RUNNING 결과 저장은 항상 {@code lease_token}이 일치할 때만 반영되므로, 임대가 만료돼 다른 실행이
 * 인계받은 뒤 도착한 옛 응답은 버려진다(fencing token). 외부 AI 호출 자체의 정확히 한 번 실행은 보장하지 않는다.
 */
@Entity
@Table(name = "competition_extraction_jobs", indexes = {
        @Index(name = "idx_extraction_jobs_status_next", columnList = "status, next_attempt_at"),
        @Index(name = "idx_extraction_jobs_status_lease", columnList = "status, lease_expires_at"),
        @Index(name = "idx_extraction_jobs_requester_created", columnList = "requester_user_id, created_at"),
        @Index(name = "idx_extraction_jobs_hash_status", columnList = "source_url_hash, status"),
        @Index(name = "idx_extraction_jobs_expires", columnList = "expires_at")
}, uniqueConstraints = @UniqueConstraint(name = "uk_extraction_jobs_active_key", columnNames = "active_key"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompetitionExtractionJob {
    public static final int MAX_ERROR_MESSAGE_LENGTH = 300;
    public static final int MAX_ERROR_DETAIL_LENGTH = 500;
    public static final int MAX_RESULT_LENGTH = 512_000;

    public enum Status { PENDING, RUNNING, SUCCEEDED, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 이 작업을 처음 접수한 회원. 신규 AI 작업을 만든 비용(시간당 생성 한도·진행 중 한도)은 이 회원에게 센다.
     * 같은 URL에 나중에 합류한 회원은 {@link CompetitionExtractionJobSubscription}으로만 기록하며 조회 권한도 거기서 판단한다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_extraction_jobs_requester"))
    private User requester;

    /** 회원이 입력한 URL(trim). AI 서버에는 이 값을 보낸다. */
    @Column(name = "source_url", nullable = false, length = 2000)
    private String sourceUrl;

    /** 기존 공모전 URL 중복 판단과 같은 정규화 hash. */
    @Column(name = "source_url_hash", nullable = false, length = 64)
    private String sourceUrlHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    /**
     * PENDING/RUNNING 동안만 URL hash 값을 가지고, 종료되면 NULL이 된다.
     * MySQL UNIQUE는 NULL을 여러 개 허용하므로 같은 URL의 진행 중 작업을 사용자와 무관하게 DB에서 하나로 제한한다.
     */
    @Column(name = "active_key", length = 90)
    private String activeKey;

    /** 선점(claim)될 때마다 1 증가한다. 임대 만료 회수도 시도로 센다. */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    /** PENDING 작업을 선점할 수 있는 가장 이른 시각(재시도 backoff). */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    /** RUNNING 실행의 임대 만료 시각. 지나면 회수 대상이다. */
    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    /** 현재 RUNNING 실행을 식별하는 fencing token. 결과 저장 UPDATE의 조건으로 쓴다. */
    @Column(name = "lease_token", length = 36)
    private String leaseToken;

    /** AI 원본 응답 JSON. 조회 때 기존 DTO 변환·필드 검증 로직으로 미리보기를 다시 만든다. */
    @Column(name = "result_json", columnDefinition = "mediumtext")
    private String resultJson;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    /** 회원에게 보여도 되는 정리된 문구만 저장한다(내부 예외 메시지·스택 제외). */
    @Column(name = "error_message", length = MAX_ERROR_MESSAGE_LENGTH)
    private String errorMessage;

    /** 관리자 전용 원인 요약(오류 종류·HTTP 상태). 회원 응답에는 내보내지 않는다. */
    @Column(name = "internal_error_detail", length = MAX_ERROR_DETAIL_LENGTH)
    private String errorDetail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** 종료 작업을 정리(삭제)할 수 있는 시각. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    public static CompetitionExtractionJob pending(User requester, String sourceUrl, String sourceUrlHash, Instant now) {
        CompetitionExtractionJob job = new CompetitionExtractionJob();
        job.requester = requester;
        job.sourceUrl = sourceUrl;
        job.sourceUrlHash = sourceUrlHash;
        job.status = Status.PENDING;
        job.activeKey = sourceUrlHash;
        job.attemptCount = 0;
        job.nextAttemptAt = now;
        job.createdAt = now;
        job.updatedAt = now;
        return job;
    }

    public boolean isTerminal() {
        return status == Status.SUCCEEDED || status == Status.FAILED;
    }

    /** SKIP LOCKED로 잠근 PENDING 행을 새 실행에 넘긴다. 새 token이 이전 실행의 결과 저장을 무효화한다. */
    public void claim(String token, Instant leaseExpiresAt, Instant now) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Only pending extraction jobs can be claimed");
        }
        status = Status.RUNNING;
        leaseToken = token;
        this.leaseExpiresAt = leaseExpiresAt;
        nextAttemptAt = null;
        attemptCount++;
        updatedAt = now;
    }

    /**
     * 임대가 만료된 실행을 회수한다. 이전 실행이 아직 살아 있을 수 있으므로 token을 지워 늦은 응답의 저장을 막는다.
     * 시도 횟수가 남으면 backoff 뒤 다시 대기시키고, 아니면 최종 실패로 정리한다.
     */
    public void recoverExpiredLease(int maxAttempts, Instant retryAt, Instant now, Instant terminalExpiresAt) {
        if (status != Status.RUNNING) {
            throw new IllegalStateException("Only running extraction jobs can be recovered");
        }
        leaseToken = null;
        leaseExpiresAt = null;
        updatedAt = now;
        errorCode = "LEASE_EXPIRED";
        errorMessage = "추출 실행이 제한 시간 안에 끝나지 않았습니다.";
        errorDetail = "임대 만료로 회수(서버 재시작 또는 응답 지연), 시도 " + attemptCount + "회";
        if (attemptCount < maxAttempts) {
            status = Status.PENDING;
            nextAttemptAt = retryAt;
        } else {
            finish(Status.FAILED, now, terminalExpiresAt);
        }
    }

    /** 대기 제한 시간을 넘긴 PENDING 작업을 최종 실패로 정리한다. */
    public void expirePending(Instant now, Instant terminalExpiresAt) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Only pending extraction jobs can expire");
        }
        errorCode = "QUEUE_TIMEOUT";
        errorMessage = "추출 대기 시간이 초과됐습니다. 잠시 후 다시 요청해 주세요.";
        errorDetail = "대기열에서 실행되지 못한 채 대기 제한 시간을 넘김";
        finish(Status.FAILED, now, terminalExpiresAt);
    }

    private void finish(Status terminal, Instant now, Instant terminalExpiresAt) {
        status = terminal;
        activeKey = null;
        nextAttemptAt = null;
        completedAt = now;
        expiresAt = terminalExpiresAt;
    }
}
