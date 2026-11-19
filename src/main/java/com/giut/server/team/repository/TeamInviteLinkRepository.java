package com.giut.server.team.repository;

import com.giut.server.team.entity.TeamInviteLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamInviteLinkRepository extends JpaRepository<TeamInviteLink, Long> {

    Optional<TeamInviteLink> findByTeamIdAndTokenHash(Long teamId, String tokenHash);

    Optional<TeamInviteLink> findByIdAndTeamId(Long id, Long teamId);
}
