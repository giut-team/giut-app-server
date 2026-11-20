package com.giut.server.team.repository;

import com.giut.server.profile.entity.ProfileRole;
import com.giut.server.team.entity.TeamMember;
import com.giut.server.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    boolean existsByTeamIdAndUserIdAndStatus(Long teamId, Long userId, TeamMember.Status status);

    long countByTeamIdAndStatus(Long teamId, TeamMember.Status status);

    long countByTeamIdAndRoleCodeAndStatusAndRole(
            Long teamId, String roleCode, TeamMember.Status status, TeamMember.Role role
    );

    @Query("""
            select member.id as teamMemberId, member.userId as userId, account.nickname as nickname,
                   member.role as role, member.roleCode as roleCode, member.status as status,
                   member.joinedAt as joinedAt, profileRole.name as roleName, profileRole.primaryRole as primaryRole
            from TeamMember member
            join User account on account.id = member.userId
            left join ProfileRole profileRole on profileRole.code = member.roleCode
            where member.teamId = :teamId and member.status = :memberStatus and account.status = :userStatus
            order by member.id
            """)
    List<TeamMemberDetails> findMemberDetails(
            @Param("teamId") Long teamId,
            @Param("memberStatus") TeamMember.Status memberStatus,
            @Param("userStatus") User.Status userStatus
    );

    interface TeamMemberDetails {
        Long getTeamMemberId();
        Long getUserId();
        String getNickname();
        TeamMember.Role getRole();
        String getRoleCode();
        TeamMember.Status getStatus();
        Instant getJoinedAt();
        String getRoleName();
        ProfileRole.PrimaryRole getPrimaryRole();
    }

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
