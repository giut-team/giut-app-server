package com.giut.server.team.repository;

import com.giut.server.team.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    boolean existsByTeamIdAndUserIdAndStatus(Long teamId, Long userId, TeamMember.Status status);

    long countByTeamIdAndStatus(Long teamId, TeamMember.Status status);

    long countByTeamIdAndRoleCodeAndStatus(Long teamId, String roleCode, TeamMember.Status status);

    @Query("""
            select member.teamId as teamId, count(member) as memberCount
            from TeamMember member
            where member.teamId in :teamIds and member.status = :status
            group by member.teamId
            """)
    List<TeamMemberCount> countByTeamIdsAndStatus(
            @Param("teamIds") Collection<Long> teamIds,
            @Param("status") TeamMember.Status status
    );

    interface TeamMemberCount {
        Long getTeamId();

        long getMemberCount();
    }

    List<TeamMember> findAllByTeamIdAndStatus(Long teamId, TeamMember.Status status);

    List<TeamMember> findAllByUserIdAndStatus(Long userId, TeamMember.Status status);

    long countByUserIdAndStatus(Long userId, TeamMember.Status status);

    Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);
}
