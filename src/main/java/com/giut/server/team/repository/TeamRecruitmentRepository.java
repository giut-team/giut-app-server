package com.giut.server.team.repository;

import com.giut.server.team.entity.TeamRecruitment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRecruitmentRepository extends JpaRepository<TeamRecruitment, Long> {

    List<TeamRecruitment> findAllByTeamIdOrderByIdAsc(Long teamId);

    Optional<TeamRecruitment> findByTeamIdAndRoleCode(Long teamId, String roleCode);
}
