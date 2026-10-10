package com.giut.server.team.repository;

import com.giut.server.team.entity.TeamApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamApplicationRepository extends JpaRepository<TeamApplication, Long> {

    boolean existsByTeamIdAndUserIdAndStatus(Long teamId, Long userId, TeamApplication.Status status);

    List<TeamApplication> findAllByTeamIdAndStatus(Long teamId, TeamApplication.Status status);

    List<TeamApplication> findAllByUserIdOrderByAppliedAtDesc(Long userId);

    Optional<TeamApplication> findByIdAndTeamId(Long id, Long teamId);

    List<TeamApplication> findAllByTeamIdAndStatusAndType(
            Long teamId,
            TeamApplication.Status status,
            TeamApplication.Type type
    );

    List<TeamApplication> findAllByUserIdAndTypeOrderByAppliedAtDesc(
            Long userId,
            TeamApplication.Type type
    );

    long countByUserIdAndTypeAndStatus(
            Long userId,
            TeamApplication.Type type,
            TeamApplication.Status status
    );

    List<TeamApplication> findAllByTeamIdAndTypeOrderByAppliedAtDesc(
            Long teamId,
            TeamApplication.Type type
    );
}
