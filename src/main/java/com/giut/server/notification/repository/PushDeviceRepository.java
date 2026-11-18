package com.giut.server.notification.repository;

import com.giut.server.notification.entity.PushDevice;
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

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PushDevice d where d.installationId = :installationId")
    Optional<PushDevice> findByInstallationIdForUpdate(@Param("installationId") String installationId);

    /** 같은 토큰을 가진 다른 설치(앱 재설치·다른 계정 로그인으로 남은 행). */
    @Query("""
            select d from PushDevice d
            where d.pushToken = :token and d.active = true and d.installationId <> :installationId
            """)
    List<PushDevice> findActiveByTokenExcept(@Param("token") String token, @Param("installationId") String installationId);

    @Query("select d from PushDevice d where d.user.id in :userIds and d.active = true")
    List<PushDevice> findActiveByUserIds(@Param("userIds") Collection<Long> userIds);

    @Query("select d from PushDevice d where d.active = true and d.lastSeenAt < :cutoff order by d.id")
    List<PushDevice> findStale(@Param("cutoff") Instant cutoff, Limit limit);
}
