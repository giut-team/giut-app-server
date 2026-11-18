package com.giut.server.notification.repository;

import com.giut.server.notification.entity.Notification;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 최신순 커서 페이지. cursor가 null이면 첫 페이지다. (user_id, id) 인덱스로 읽는다. */
    @Query("""
            select n from Notification n
            where n.user.id = :userId and (:cursor is null or n.id < :cursor)
            order by n.id desc
            """)
    List<Notification> findPage(@Param("userId") Long userId, @Param("cursor") Long cursor, Limit limit);

    Optional<Notification> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_IdAndReadAtIsNull(Long userId);

    /** 회원마다 [회원 id, 안 읽은 알림 수]. 푸시 배지 숫자를 한 번에 센다. */
    @Query("""
            select n.user.id, count(n) from Notification n
            where n.user.id in :userIds and n.readAt is null
            group by n.user.id
            """)
    List<Object[]> countUnreadByUserIds(@Param("userIds") Collection<Long> userIds);

    @Query("select n.dedupKey from Notification n where n.dedupKey in :keys")
    List<String> findExistingDedupKeys(@Param("keys") Collection<String> keys);

    /** 요청 시각까지 만들어진 안 읽은 알림만 읽음 처리한다. 요청 중에 새로 생긴 알림은 남긴다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notification n set n.readAt = :now
            where n.user.id = :userId and n.readAt is null and n.createdAt <= :now
            """)
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);
}
