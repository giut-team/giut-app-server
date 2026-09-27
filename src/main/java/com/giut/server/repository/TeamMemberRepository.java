package com.giut.server.repository;

import com.giut.server.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    boolean existsByTeamIdAndUserIdAndStatus(Long teamId, Long userId, TeamMember.Status status);

    long countByTeamIdAndStatus(Long teamId, TeamMember.Status status);

    List<TeamMember> findAllByTeamIdAndStatus(Long teamId, TeamMember.Status status);

    List<TeamMember> findAllByUserIdAndStatus(Long userId, TeamMember.Status status);

    Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);
}
