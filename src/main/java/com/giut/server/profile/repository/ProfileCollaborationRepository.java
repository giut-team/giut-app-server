package com.giut.server.profile.repository;

import com.giut.server.team.entity.TeamMember;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Team 코드를 변경하지 않고 프로필의 누적 팀 참여 이력을 조회한다. */
public interface ProfileCollaborationRepository extends Repository<TeamMember, Long> {
    @Query("select count(distinct member.teamId) from TeamMember member where member.userId = :userId")
    long countParticipatedTeams(@Param("userId") Long userId);
}
