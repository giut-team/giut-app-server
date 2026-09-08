package com.giut.server.entity;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELED
    }

    public static TeamApplication create(Long teamId, Long userId, String message) {
        TeamApplication application = new TeamApplication();
        application.teamId = teamId;
        application.userId = userId;
        application.message = message;
        application.status = Status.PENDING;
        application.appliedAt = Instant.now();
        return application;
    }

    public void approve() {
        this.status = Status.APPROVED;
        this.decidedAt = Instant.now();
    }

    public void reject() {
        this.status = Status.REJECTED;
        this.decidedAt = Instant.now();
    }

    public void cancel() {
        this.status = Status.CANCELED;
        this.decidedAt = Instant.now();
    }
}
