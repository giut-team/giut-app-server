package com.giut.server.team.repository;

import com.giut.server.team.entity.TeamApplication;
import com.giut.server.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamApplicationRepository extends JpaRepository<TeamApplication, Long> {

    boolean existsByTeamIdAndUserIdAndStatus(Long teamId, Long userId, TeamApplication.Status status);

    List<TeamApplication> findAllByTeamIdAndStatus(Long teamId, TeamApplication.Status status);

    List<TeamApplication> findAllByUserIdOrderByAppliedAtDesc(Long userId);

    @Query("""
            select application.teamId as teamId, count(application.id) as applicationCount
            from TeamApplication application
            join User account on account.id = application.userId and account.status = :userStatus
            where application.teamId in :teamIds and application.type = :type and application.status = :status
            group by application.teamId
            """)
    List<TeamApplicationCount> countByTeamIdsAndTypeAndStatusAndUserStatus(
            @Param("teamIds") Collection<Long> teamIds,
            @Param("type") TeamApplication.Type type,
            @Param("status") TeamApplication.Status status,
            @Param("userStatus") User.Status userStatus
    );

    interface TeamApplicationCount {
        Long getTeamId();
        long getApplicationCount();
    }

    @Query("""
            select application as application, account.nickname as applicantNickname
            from TeamApplication application
            join User account on account.id = application.userId and account.status = :userStatus
            where application.teamId = :teamId and application.type = :type and application.status = :status
            order by application.appliedAt desc, application.id desc
            """)
    List<PendingApplicationDetails> findPendingApplicationDetails(
            @Param("teamId") Long teamId,
            @Param("type") TeamApplication.Type type,
            @Param("status") TeamApplication.Status status,
            @Param("userStatus") User.Status userStatus
    );

    interface PendingApplicationDetails {
        TeamApplication getApplication();
        String getApplicantNickname();
    }

    Optional<TeamApplication> findByIdAndTeamId(Long id, Long teamId);

    List<TeamApplication> findAllByTeamIdAndStatusAndType(
            Long teamId,
            TeamApplication.Status status,
            TeamApplication.Type type
    );

    List<TeamApplication> findAllByUserIdAndTypeOrderByAppliedAtDesc(
            Long userId,
            TeamApplication.Type type
    );

    long countByUserIdAndTypeAndStatus(
            Long userId,
            TeamApplication.Type type,
            TeamApplication.Status status
    );

    List<TeamApplication> findAllByTeamIdAndTypeOrderByAppliedAtDesc(
            Long teamId,
            TeamApplication.Type type
    );
}
