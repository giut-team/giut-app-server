package com.giut.server.repository;

import com.giut.server.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

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
}
