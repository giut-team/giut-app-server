package com.giut.server.competition.service;

import com.giut.server.competition.config.CompetitionExtractionProperties;
import com.giut.server.competition.entity.CompetitionExtractionJob;
import com.giut.server.competition.entity.CompetitionExtractionJob.Status;
import com.giut.server.competition.repository.CompetitionExtractionJobRepository;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.global.exception.TooManyRequestsException;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 추출 작업의 모든 DB 상태 전이. 각 public 메서드가 하나의 짧은 트랜잭션이며 외부 I/O(DNS·AI)를 하지 않는다.
 * 작업자·서비스와 다른 bean으로 두어 Spring 트랜잭션 proxy가 항상 적용되게 한다(self-invocation 회피).
 */
@Component
@RequiredArgsConstructor
public class CompetitionExtractionJobStore {
    private static final Set<Status> ACTIVE = EnumSet.of(Status.PENDING, Status.RUNNING);
    private static final int CANDIDATE_FACTOR = 4;
    private static final int MAX_CANDIDATES = 64;

    private final CompetitionExtractionJobRepository jobs;
    private final UserRepository users;
    private final CompetitionExtractionProperties properties;

    public record ClaimedJob(Long id, String token, String sourceUrl, int attemptCount) {
    }

    public record Enqueued(CompetitionExtractionJob job, boolean created) {
    }

    /**
     * 사용자 행을 잠가 같은 사용자의 접수를 직렬화한 뒤, 재사용 가능한 작업이 있으면 그것을 돌려주고 없으면 한도 검사 후 새 작업을 저장한다.
     * 메서드가 끝나며 commit되므로 호출자가 응답할 때는 작업이 이미 DB에 있다.
     */
    @Transactional
    public Enqueued enqueue(Long userId, String sourceUrl, String sourceUrlHash, Instant now) {
        User requester = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        Optional<CompetitionExtractionJob> reusable = findReusable(userId, sourceUrlHash, now);
        if (reusable.isPresent()) {
            return new Enqueued(reusable.get(), false);
        }
        if (jobs.countByRequesterIdAndStatusIn(userId, ACTIVE) >= properties.maxActivePerUser()) {
            throw new TooManyRequestsException("진행 중인 공모전 추출 작업이 너무 많습니다. 기존 작업이 끝난 뒤 다시 요청해 주세요.", 30);
        }
        if (jobs.countByRequesterIdAndCreatedAtAfter(userId, now.minus(Duration.ofHours(1)))
                >= properties.maxRequestsPerUserPerHour()) {
            throw new TooManyRequestsException("공모전 추출 요청 한도를 초과했습니다. 잠시 후 다시 요청해 주세요.", 600);
        }
        // 전체 상한은 여러 사용자의 동시 접수에서 몇 건 초과할 수 있는 근사 검사다(사용자 단위 잠금만 사용).
        if (jobs.countByStatusIn(ACTIVE) >= properties.maxActiveTotal()) {
            throw new ExtractionQueueSaturated(30);
        }
        CompetitionExtractionJob job = jobs.saveAndFlush(
                CompetitionExtractionJob.pending(requester, sourceUrl, sourceUrlHash, now));
        return new Enqueued(job, true);
    }

    @Transactional(readOnly = true)
    public Optional<CompetitionExtractionJob> findReusable(Long userId, String sourceUrlHash, Instant now) {
        return jobs.findReusable(userId, sourceUrlHash, now, Limit.of(1)).stream().findFirst();
    }

    /**
     * 최대 limit건을 선점해 RUNNING·새 token·임대 만료 시각으로 commit한다. AI 호출은 이 트랜잭션이 끝난 뒤에 한다.
     * 잠근 후보가 이미 대기 기한(접수 시각 + pending-timeout)을 넘었으면 실행하지 않고 같은 트랜잭션에서 QUEUE_TIMEOUT으로 끝낸다.
     * 재시작 직후처럼 선점이 대기 만료 정리({@link #expireStalePending})보다 먼저 돌아도 기한 지난 작업이 AI로 가지 않게 한다.
     */
    @Transactional
    public List<ClaimedJob> claim(int limit, Instant now) {
        if (limit <= 0) {
            return List.of();
        }
        // 다른 작업자와 후보가 겹쳐도 남은 후보에서 채울 수 있게 몇 배수를 읽는다. 실제로 RUNNING으로 바꾸는 행은 limit 이하다.
        List<Long> candidates = jobs.findDuePendingIds(now, Math.min(limit * CANDIDATE_FACTOR, MAX_CANDIDATES));
        Instant leaseExpiresAt = now.plus(properties.leaseDuration());
        Instant pendingCutoff = now.minus(properties.pendingTimeout());
        List<ClaimedJob> claimed = new ArrayList<>();
        for (Long id : candidates) {
            if (claimed.size() >= limit) {
                break;
            }
            jobs.lockIfAvailable(id)
                    .filter(job -> job.getStatus() == Status.PENDING && job.getNextAttemptAt() != null
                            && !job.getNextAttemptAt().isAfter(now))
                    .ifPresent(job -> {
                        if (!job.getCreatedAt().isAfter(pendingCutoff)) {
                            // expireStalePending과 같은 경계(created_at <= cutoff). 시도 횟수를 늘리지 않고 limit에도 세지 않는다.
                            job.expirePending(now, now.plus(properties.resultRetention()));
                            return;
                        }
                        job.claim(UUID.randomUUID().toString(), leaseExpiresAt, now);
                        claimed.add(new ClaimedJob(job.getId(), job.getLeaseToken(), job.getSourceUrl(),
                                job.getAttemptCount()));
                    });
        }
        return claimed;
    }

    /** token이 아직 현재 실행의 것일 때만 결과를 저장한다. false면 회수·인계된 옛 실행의 응답이므로 버린다. */
    @Transactional
    public boolean recordSuccess(ClaimedJob claimed, String resultJson, Instant now) {
        return jobs.markSucceeded(claimed.id(), claimed.token(), resultJson, now,
                now.plus(properties.resultRetention())) == 1;
    }

    /** 일시 실패이고 시도 횟수가 남으면 backoff 뒤 PENDING으로, 아니면 최종 실패로 저장한다. token 조건은 성공과 같다. */
    @Transactional
    public boolean recordFailure(ClaimedJob claimed, String code, String message, boolean retryable, Instant now) {
        String safeMessage = message.length() > CompetitionExtractionJob.MAX_ERROR_MESSAGE_LENGTH
                ? message.substring(0, CompetitionExtractionJob.MAX_ERROR_MESSAGE_LENGTH) : message;
        if (retryable && claimed.attemptCount() < properties.maxAttempts()) {
            return jobs.markRetry(claimed.id(), claimed.token(), code, safeMessage,
                    now.plus(properties.retryDelay(claimed.attemptCount())), now) == 1;
        }
        return jobs.markFailed(claimed.id(), claimed.token(), code, safeMessage, now,
                now.plus(properties.resultRetention())) == 1;
    }

    /** 임대가 만료된 RUNNING 작업을 재대기 또는 최종 실패로 회수한다. 서버 재시작으로 멈춘 작업도 여기서 복구된다. */
    @Transactional
    public int recoverExpiredLeases(Instant now) {
        List<CompetitionExtractionJob> expired = lockMatching(jobs.findExpiredLeaseIds(now, properties.maintenanceBatchSize()),
                job -> job.getStatus() == Status.RUNNING && job.getLeaseExpiresAt() != null
                        && !job.getLeaseExpiresAt().isAfter(now));
        expired.forEach(job -> job.recoverExpiredLease(properties.maxAttempts(),
                now.plus(properties.retryDelay(job.getAttemptCount())), now, now.plus(properties.resultRetention())));
        return expired.size();
    }

    @Transactional
    public int expireStalePending(Instant now) {
        Instant cutoff = now.minus(properties.pendingTimeout());
        List<CompetitionExtractionJob> stale = lockMatching(jobs.findStalePendingIds(cutoff, properties.maintenanceBatchSize()),
                job -> job.getStatus() == Status.PENDING && !job.getCreatedAt().isAfter(cutoff));
        stale.forEach(job -> job.expirePending(now, now.plus(properties.resultRetention())));
        return stale.size();
    }

    /** 후보를 PK 단건 SKIP LOCKED로 잠그고, 잠근 시점의 최신 값이 여전히 조건에 맞는 행만 돌려준다. */
    private List<CompetitionExtractionJob> lockMatching(List<Long> candidates, Predicate<CompetitionExtractionJob> condition) {
        List<CompetitionExtractionJob> locked = new ArrayList<>();
        for (Long id : candidates) {
            jobs.lockIfAvailable(id).filter(condition).ifPresent(locked::add);
        }
        return locked;
    }

    /** 보관 기간이 지난 종료 작업을 한 번에 최대 maintenanceBatchSize건 삭제한다. */
    @Transactional
    public int purgeExpired(Instant now) {
        List<Long> ids = jobs.findExpiredTerminalIds(now, Limit.of(properties.maintenanceBatchSize()));
        if (!ids.isEmpty()) {
            jobs.deleteAllByIdInBatch(ids);
        }
        return ids.size();
    }

    /** 전체 진행 중 작업이 상한에 도달해 신규 접수를 받지 않는 상태(503 + Retry-After). */
    public static class ExtractionQueueSaturated extends RuntimeException {
        private final long retryAfterSeconds;

        public ExtractionQueueSaturated(long retryAfterSeconds) {
            super("공모전 추출 요청이 많아 지금은 접수할 수 없습니다. 잠시 후 다시 요청해 주세요.");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
