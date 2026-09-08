package com.giut.server.repository;

import com.giut.server.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findAllByCompetitionId(Long competitionId);

    List<Team> findAllByLeaderUserId(Long leaderUserId);
}
