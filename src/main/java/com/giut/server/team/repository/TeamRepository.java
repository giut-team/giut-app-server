package com.giut.server.team.repository;

import com.giut.server.team.entity.Team;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findAllByCompetition_IdOrderByIdDesc(Long competitionId);

    @Query("""
            select team.competition.id as competitionId, count(team.id) as teamCount
            from Team team
            where team.competition.id in :competitionIds
            group by team.competition.id
            """)
    List<CompetitionTeamCount> countByCompetitionIds(@Param("competitionIds") Collection<Long> competitionIds);

    interface CompetitionTeamCount {
        Long getCompetitionId();

        long getTeamCount();
    }

    List<Team> findAllByLeader_Id(Long leaderUserId);

    boolean existsByCompetition_IdAndLeader_IdAndStatus(
            Long competitionId, Long leaderUserId, Team.Status status);

    Page<Team> findAllByCompetition_IdAndStatus(Long competitionId, Team.Status status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select team from Team team where team.id = :teamId")
    Optional<Team> findByIdForUpdate(@Param("teamId") Long teamId);
}
