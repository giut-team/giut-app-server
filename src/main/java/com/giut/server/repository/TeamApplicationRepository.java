package com.giut.server.repository;

import com.giut.server.entity.TeamApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamApplicationRepository extends JpaRepository<TeamApplication, Long> {

    boolean existsByTeamIdAndUserIdAndStatus(Long teamId, Long userId, TeamApplication.Status status);

    List<TeamApplication> findAllByTeamIdAndStatus(Long teamId, TeamApplication.Status status);

    Optional<TeamApplication> findByIdAndTeamId(Long id, Long teamId);
}
