package com.giut.server.team.entity;

import com.giut.server.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "team_applications",
        indexes = {
                @Index(name = "idx_team_applications_team_id", columnList = "team_id"),
                @Index(name = "idx_team_applications_user_id", columnList = "user_id"),
                @Index(name = "idx_team_applications_team_status", columnList = "team_id, status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamApplication extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 500)
    private String message;

    @Column(name = "role_code", length = 40)
    private String roleCode;

    @Column(name = "assigned_role_code", length = 40)
    private String assignedRoleCode;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    public enum Type {
        APPLICATION, // 사용자가 팀에 지원
        INVITATION // 팀장이 팀원에게 제안
    }

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELED
    }

    public static TeamApplication create(Long teamId, Long userId, String roleCode, String message) {
        TeamApplication application = new TeamApplication();
        application.teamId = teamId;
        application.userId = userId;
        application.roleCode = roleCode;
        application.message = message;
        application.status = Status.PENDING;
        application.appliedAt = Instant.now();
        return application;
    }

    public static TeamApplication createInvitation(Long teamId, Long userId, String roleCode, String message) {

        TeamApplication invitation = create(teamId, userId, roleCode, message);

        invitation.type = Type.INVITATION;

        return invitation;
    }

    public void approve(String assignedRoleCode) {
        this.status = Status.APPROVED;
        this.assignedRoleCode = assignedRoleCode;
        this.decidedAt = Instant.now();
    }

    public void reject(String rejectionReason) {
        this.status = Status.REJECTED;
        this.rejectionReason = rejectionReason;
        this.decidedAt = Instant.now();
    }

    public void cancel() {
        this.status = Status.CANCELED;
        this.decidedAt = Instant.now();
    }
}
