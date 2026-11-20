package com.giut.server.team.repository;

import com.giut.server.team.entity.TeamInviteLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TeamInviteLinkRepository extends JpaRepository<TeamInviteLink, Long> {

    List<TeamInviteLink> findAllByTeamIdAndExpiresAtAfterAndUsedAtIsNullAndRevokedAtIsNullOrderByIdDesc(
            Long teamId, Instant now
    );

    long countByTeamIdAndExpiresAtAfterAndUsedAtIsNullAndRevokedAtIsNull(Long teamId, Instant now);

    long countByTeamIdAndExpiresAtAfter(Long teamId, Instant now);

    Optional<TeamInviteLink> findByTeamIdAndTokenHash(Long teamId, String tokenHash);

    Optional<TeamInviteLink> findByIdAndTeamId(Long id, Long teamId);
}
