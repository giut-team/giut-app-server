package com.giut.server.notification.repository;

import com.giut.server.notification.entity.PushDelivery;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 선점은 추출 작업과 같은 2단계다. 잠금 없는 후보 id 조회(인덱스) → 후보마다 PK 단건 {@code FOR UPDATE SKIP LOCKED}
 * → Java에서 조건 재확인. MySQL에서 한 단계 {@code WHERE status ... LIMIT n FOR UPDATE SKIP LOCKED}는 읽은 행을 모두
 * 잠글 수 있어 쓰지 않는다(docs/harness/database.md 2026-11-13).
 */
public interface PushDeliveryRepository extends JpaRepository<PushDelivery, Long> {

    @Query(value = """
            SELECT id FROM push_deliveries
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at, id LIMIT :limit
            """, nativeQuery = true)
    List<Long> findDueIds(@Param("now") Instant now, @Param("limit") int limit);

    @Query(value = """
            SELECT id FROM push_deliveries
            WHERE status = 'SENDING' AND lease_expires_at <= :now
            ORDER BY lease_expires_at, id LIMIT :limit
            """, nativeQuery = true)
    List<Long> findExpiredLeaseIds(@Param("now") Instant now, @Param("limit") int limit);

    @Query(value = """
            SELECT id FROM push_deliveries
            WHERE status = 'PENDING' AND created_at <= :cutoff
            ORDER BY id LIMIT :limit
            """, nativeQuery = true)
    List<Long> findStalePendingIds(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    @Query(value = "SELECT * FROM push_deliveries WHERE id = :id FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<PushDelivery> lockIfAvailable(@Param("id") Long id);

    /** 결과 저장용. 선점한 실행이 기다려서라도 반드시 반영해야 하므로 SKIP하지 않는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PushDelivery d where d.id = :id")
    Optional<PushDelivery> lockById(@Param("id") Long id);

    /** 발송 메시지를 만들 알림·기기·회원을 한 번에 읽는다(행마다 지연 로딩하지 않는다). */
    @Query("""
            select d from PushDelivery d
            join fetch d.notification n join fetch n.user
            join fetch d.device v join fetch v.user
            where d.id in :ids
            """)
    List<PushDelivery> findWithTargets(@Param("ids") Collection<Long> ids);

    @Query("""
            select d from PushDelivery d
            where d.status = com.giut.server.notification.entity.PushDelivery.Status.SENT and d.receiptCheckAt <= :now
            order by d.receiptCheckAt, d.id
            """)
    List<PushDelivery> findReceiptDue(@Param("now") Instant now, Limit limit);

    @Query("select d.id from PushDelivery d where d.completedAt < :cutoff order by d.id")
    List<Long> findCompletedBefore(@Param("cutoff") Instant cutoff, Limit limit);
}
