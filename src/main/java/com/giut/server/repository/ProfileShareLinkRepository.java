package com.giut.server.repository;

import com.giut.server.entity.ProfileShareLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProfileShareLinkRepository extends JpaRepository<ProfileShareLink, Long> {

    Optional<ProfileShareLink> findByTokenHash(String tokenHash);

    Optional<ProfileShareLink> findByIdAndUser_Id(Long id, Long userId);

    List<ProfileShareLink> findAllByUser_IdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            Long userId,
            Instant now
    );
}
