package com.giut.server.competition.repository;

import com.giut.server.competition.entity.CompetitionExtractionJob;
import com.giut.server.competition.entity.CompetitionExtractionJob.Status;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompetitionExtractionJobRepository extends JpaRepository<CompetitionExtractionJob, Long> {

    /*
     * 선점·회수는 두 단계로 한다.
     * 1) 잠금 없는 일반 SELECT로 후보 id를 순서대로 고른다((status, 시각) 인덱스만 읽는 covering 조회).
     * 2) 후보를 PK 한 건씩 FOR UPDATE SKIP LOCKED로 잠그고, 최신 commit 값으로 조건을 Java에서 다시 확인한다.
     * 실제 MySQL(행이 적은 테이블)에서 한 단계 "WHERE status ... ORDER BY ... LIMIT n FOR UPDATE SKIP LOCKED"는
     * 전체 스캔+정렬 계획이 선택돼 LIMIT과 무관하게 읽은 모든 대기 행을 잠갔고, "id IN (...) AND status ..."도 status 인덱스
     * 스캔+정렬로 같은 문제가 있었다. PK 단건 조회는 그 행 하나만 잠그므로 다른 작업자가 나머지 작업을 가져갈 수 있다.
     */

    @Query(value = """
            SELECT id FROM competition_extraction_jobs
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at, id
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findDuePendingIds(@Param("now") Instant now, @Param("limit") int limit);

    @Query(value = """
            SELECT id FROM competition_extraction_jobs
            WHERE status = 'RUNNING' AND lease_expires_at <= :now
            ORDER BY lease_expires_at, id
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findExpiredLeaseIds(@Param("now") Instant now, @Param("limit") int limit);

    @Query(value = """
            SELECT id FROM competition_extraction_jobs
            WHERE status = 'PENDING' AND created_at <= :cutoff
            ORDER BY created_at, id
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findStalePendingIds(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    /**
     * PK 한 행만 잠근다. 다른 트랜잭션이 잡고 있으면 기다리지 않고 비어 있는 결과를 돌려준다.
     * 반드시 상태 전이 후 바로 commit하는 짧은 트랜잭션 안에서만 호출하고, 반환된 행의 조건을 호출자가 다시 확인한다.
     */
    @Query(value = "SELECT * FROM competition_extraction_jobs WHERE id = :id FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<CompetitionExtractionJob> lockIfAvailable(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CompetitionExtractionJob j
            set j.status = com.giut.server.competition.entity.CompetitionExtractionJob.Status.SUCCEEDED,
                j.resultJson = :resultJson, j.errorCode = null, j.errorMessage = null,
                j.leaseToken = null, j.leaseExpiresAt = null, j.nextAttemptAt = null, j.activeKey = null,
                j.completedAt = :now, j.updatedAt = :now, j.expiresAt = :expiresAt
            where j.id = :id and j.leaseToken = :token
              and j.status = com.giut.server.competition.entity.CompetitionExtractionJob.Status.RUNNING
            """)
    int markSucceeded(@Param("id") Long id, @Param("token") String token, @Param("resultJson") String resultJson,
                      @Param("now") Instant now, @Param("expiresAt") Instant expiresAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CompetitionExtractionJob j
            set j.status = com.giut.server.competition.entity.CompetitionExtractionJob.Status.FAILED,
                j.errorCode = :code, j.errorMessage = :message,
                j.leaseToken = null, j.leaseExpiresAt = null, j.nextAttemptAt = null, j.activeKey = null,
                j.completedAt = :now, j.updatedAt = :now, j.expiresAt = :expiresAt
            where j.id = :id and j.leaseToken = :token
              and j.status = com.giut.server.competition.entity.CompetitionExtractionJob.Status.RUNNING
            """)
    int markFailed(@Param("id") Long id, @Param("token") String token, @Param("code") String code,
                   @Param("message") String message, @Param("now") Instant now, @Param("expiresAt") Instant expiresAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CompetitionExtractionJob j
            set j.status = com.giut.server.competition.entity.CompetitionExtractionJob.Status.PENDING,
                j.errorCode = :code, j.errorMessage = :message, j.nextAttemptAt = :nextAttemptAt,
                j.leaseToken = null, j.leaseExpiresAt = null, j.updatedAt = :now
            where j.id = :id and j.leaseToken = :token
              and j.status = com.giut.server.competition.entity.CompetitionExtractionJob.Status.RUNNING
            """)
    int markRetry(@Param("id") Long id, @Param("token") String token, @Param("code") String code,
                  @Param("message") String message, @Param("nextAttemptAt") Instant nextAttemptAt,
                  @Param("now") Instant now);

    /** 같은 URL의 진행 중 작업 또는 아직 보관 중인 성공 작업. 누가 접수했든 재사용한다(실패 작업은 재사용하지 않는다). */
    @Query("""
            select j from CompetitionExtractionJob j
            where j.sourceUrlHash = :hash
              and (j.status in (com.giut.server.competition.entity.CompetitionExtractionJob.Status.PENDING,
                                com.giut.server.competition.entity.CompetitionExtractionJob.Status.RUNNING)
                   or (j.status = com.giut.server.competition.entity.CompetitionExtractionJob.Status.SUCCEEDED
                       and j.expiresAt > :now))
            order by j.id desc
            """)
    List<CompetitionExtractionJob> findReusable(@Param("hash") String hash, @Param("now") Instant now, Limit limit);

    /** 구독한(접수했거나 같은 URL로 합류한) 회원에게만 작업을 돌려준다. 그 밖의 회원에게는 존재 여부도 드러내지 않는다. */
    @Query("""
            select j from CompetitionExtractionJob j
            where j.id = :id
              and exists (select 1 from CompetitionExtractionJobSubscription s
                          where s.job = j and s.user.id = :userId)
            """)
    Optional<CompetitionExtractionJob> findAccessible(@Param("id") Long id, @Param("userId") Long userId);

    long countByRequesterIdAndStatusIn(Long requesterId, Collection<Status> statuses);

    long countByRequesterIdAndCreatedAtAfter(Long requesterId, Instant since);

    long countByStatusIn(Collection<Status> statuses);

    @Query("""
            select j.id from CompetitionExtractionJob j
            where j.expiresAt <= :now
              and j.status in (com.giut.server.competition.entity.CompetitionExtractionJob.Status.SUCCEEDED,
                               com.giut.server.competition.entity.CompetitionExtractionJob.Status.FAILED)
            order by j.expiresAt, j.id
            """)
    List<Long> findExpiredTerminalIds(@Param("now") Instant now, Limit limit);
}
