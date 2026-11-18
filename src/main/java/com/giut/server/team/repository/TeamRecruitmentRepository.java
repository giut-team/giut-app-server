package com.giut.server.team.repository;

import com.giut.server.team.entity.TeamMember;
import com.giut.server.team.entity.TeamRecruitment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamRecruitmentRepository extends JpaRepository<TeamRecruitment, Long> {

    List<TeamRecruitment> findAllByTeamIdOrderByIdAsc(Long teamId);

    @Query("""
            select recruitment.id as recruitmentId, recruitment.roleCode as roleCode,
                   recruitment.requiredCount as requiredCount, count(member.id) as filledCount
            from TeamRecruitment recruitment
            left join TeamMember member
              on member.teamId = recruitment.teamId and member.roleCode = recruitment.roleCode
             and member.status = :status
            where recruitment.teamId = :teamId
            group by recruitment.id, recruitment.roleCode, recruitment.requiredCount
            order by recruitment.id
            """)
    List<RecruitmentWithFilledCount> findAllWithFilledCount(
            @Param("teamId") Long teamId,
            @Param("status") TeamMember.Status status
    );

    Optional<TeamRecruitment> findByTeamIdAndRoleCode(Long teamId, String roleCode);

    interface RecruitmentWithFilledCount {
        Long getRecruitmentId();
        String getRoleCode();
        Short getRequiredCount();
        long getFilledCount();
    }
}
