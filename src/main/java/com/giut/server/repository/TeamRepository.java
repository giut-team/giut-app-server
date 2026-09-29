package com.giut.server.repository;

import com.giut.server.entity.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findAllByCompetitionId(Long competitionId);

    List<Team> findAllByLeader_Id(Long leaderUserId);

    Page<Team> findAllByCompetition_IdAndStatus(Long competitionId, Team.Status status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select team from Team team where team.id = :teamId")
    Optional<Team> findByIdForUpdate(@Param("teamId") Long teamId);
}
