package com.giut.server.repository;

import com.giut.server.entity.ProfileShareLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProfileShareLinkRepository extends JpaRepository<ProfileShareLink, Long> {

    Optional<ProfileShareLink> findByTokenHash(String tokenHash);

    Optional<ProfileShareLink> findByIdAndProfile_UserId(Long id, Long userId);

    List<ProfileShareLink> findAllByProfile_UserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            Long userId,
            Instant now
    );
}
