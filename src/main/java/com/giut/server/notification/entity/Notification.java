package com.giut.server.notification.entity;

import com.giut.server.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/**
 * 앱 내 알림함의 기록. 푸시는 이 행을 보여 주는 한 가지 전달 수단일 뿐이므로, 푸시를 못 받거나 지워도 이 행은 남는다.
 * 읽음은 푸시 도착이 아니라 사용자가 알림을 열거나 명시적으로 읽음 처리했을 때만 기록한다.
 */
@Entity
@Table(name = "notifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_notifications_dedup_key", columnNames = "dedup_key"),
        indexes = @Index(name = "idx_notifications_user_id", columnList = "user_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {
    public static final int MAX_TITLE_LENGTH = 120;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notifications_user"))
    private User user;

    // MySQL ENUM이 아닌 VARCHAR로 둔다. 새 알림 유형을 추가할 때 DDL 변경이 필요 없다.
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "notification_type", nullable = false, length = 40)
    private Type notificationType;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
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

    public static Notification create(User user, Type type, String title, String body, String actionPath,
                                      String payload, String dedupKey, Instant now) {
        Notification notification = new Notification();
        notification.user = user;
        notification.notificationType = type;
        notification.title = title;
        notification.body = body;
        notification.actionPath = actionPath;
        notification.payload = payload;
        notification.dedupKey = dedupKey;
        notification.createdAt = now;
        return notification;
    }

    /** 이미 읽은 알림은 처음 읽은 시각을 유지한다. */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public enum Type {
        TEAM_INVITED, TEAM_APPLICATION_RECEIVED, TEAM_APPLICATION_ACCEPTED, TEAM_APPLICATION_REJECTED,
        TEAM_INVITATION_ACCEPTED, TEAM_INVITATION_REJECTED, COMPETITION_DEADLINE_SOON, SYSTEM,
        /** 추출 정보가 완성돼 검토 대기로 자동 제출됐다(공개는 관리자 승인 후). */
        COMPETITION_EXTRACTION_SUBMITTED,
        /** 추출은 끝났지만 일부 정보를 회원이 보완해 제출해야 한다. */
        COMPETITION_EXTRACTION_NEEDS_REVIEW,
        /** 이미 등록된 공모전이다. */
        COMPETITION_EXTRACTION_EXISTS,
        /** 공모전·대외활동으로 볼 수 없는 공고다. */
        COMPETITION_EXTRACTION_INVALID,
        /** 재시도까지 최종 실패했다. */
        COMPETITION_EXTRACTION_FAILED
    }
}
