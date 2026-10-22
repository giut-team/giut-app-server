package com.giut.server.competition.repository;

import com.giut.server.team.entity.Team;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** 공모전 응답에 필요한 팀 정보를 조회하는 읽기 전용 저장소. */
public interface CompetitionTeamRepository extends Repository<Team, Long> {

    List<Team> findAllByCompetition_IdAndStatusOrderByIdDesc(Long competitionId, Team.Status status);

    @Query("""
            select team.competition.id as competitionId, count(team.id) as teamCount
            from Team team
            where team.competition.id in :competitionIds
              and team.status = :status
            group by team.competition.id
            """)
    List<CompetitionTeamCount> countByCompetitionIdsAndStatus(
            @Param("competitionIds") Collection<Long> competitionIds,
            @Param("status") Team.Status status
    );

    interface CompetitionTeamCount {
        Long getCompetitionId();

        long getTeamCount();
    }
}
