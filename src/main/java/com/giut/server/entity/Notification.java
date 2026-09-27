package com.giut.server.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity @Table(name = "notifications", uniqueConstraints = @UniqueConstraint(name = "uk_notifications_dedup_key", columnNames = "dedup_key")) @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notifications_user"))
    private User user;

    @Enumerated(EnumType.STRING) @Column(name = "notification_type", nullable = false, length = 40)
    private Type notificationType;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "action_path", columnDefinition = "text")
    private String actionPath;

    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "json")
    private String payload;

    @Column(name = "dedup_key", length = 200)
    private String dedupKey;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public enum Type { TEAM_INVITED, TEAM_APPLICATION_RECEIVED, TEAM_APPLICATION_ACCEPTED, TEAM_APPLICATION_REJECTED, TEAM_INVITATION_ACCEPTED, TEAM_INVITATION_REJECTED, COMPETITION_DEADLINE_SOON, SYSTEM }
}
