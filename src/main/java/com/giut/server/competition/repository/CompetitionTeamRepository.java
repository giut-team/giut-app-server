package com.giut.server.competition.repository;

import com.giut.server.team.entity.Team;
import com.giut.server.team.entity.TeamMember;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** 공모전 응답에 필요한 팀 정보를 조회하는 읽기 전용 저장소. */
public interface CompetitionTeamRepository extends Repository<Team, Long> {

    /** 상태(RECRUITING/CLOSED/ARCHIVED)와 무관한 해당 공모전의 전체 팀 수. */
    long countByCompetition_Id(Long competitionId);

    /**
     * 공모전 상세 화면용: 지정한 상태의 팀 목록과, 팀별 활성 멤버 수를 한 번의 JOIN 쿼리로 함께 가져온다.
     * TeamMember는 Team을 연관관계(@ManyToOne)가 아니라 teamId 컬럼으로만 참조하므로 경로 조인 대신 ON 조건 조인을 쓴다.
     */
    @Query("""
            select team.id as teamId, team.name as name, team.leader.id as leaderId,
                   team.description as description, team.maxMemberCount as maxMemberCount,
                   team.status as status, count(member.id) as activeMemberCount
            from Team team
            left join TeamMember member
                on member.teamId = team.id and member.status = :memberStatus
            where team.competition.id = :competitionId and team.status = :teamStatus
            group by team.id, team.name, team.leader.id, team.description, team.maxMemberCount, team.status
            order by team.id desc
            """)
    List<RecruitingTeamWithActiveMemberCount> findWithActiveMemberCount(
            @Param("competitionId") Long competitionId,
            @Param("teamStatus") Team.Status teamStatus,
            @Param("memberStatus") TeamMember.Status memberStatus
    );

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

    interface RecruitingTeamWithActiveMemberCount {
        Long getTeamId();

        String getName();

        Long getLeaderId();

        String getDescription();

        Short getMaxMemberCount();

        Team.Status getStatus();

        long getActiveMemberCount();
    }
}
