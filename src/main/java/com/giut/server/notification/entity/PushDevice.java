package com.giut.server.notification.entity;

import com.giut.server.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 푸시를 받을 앱 설치 한 건. 앱이 설치마다 만든 {@code installationId}로 같은 기기의 토큰 갱신을 한 행에 모은다.
 * 토큰은 Expo 푸시 토큰({@code ExponentPushToken[...]})이다. 로그아웃·토큰 무효(DeviceNotRegistered)·다른 계정 재등록이면
 * 행을 지우지 않고 비활성화한다(발송 기록이 이 행을 참조한다).
 */
@Entity
@Table(name = "push_devices",
        uniqueConstraints = @UniqueConstraint(name = "uk_push_devices_installation", columnNames = "installation_id"),
        indexes = {
                @Index(name = "idx_push_devices_user_active", columnList = "user_id, active"),
                @Index(name = "idx_push_devices_token", columnList = "push_token")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDevice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_push_devices_user"))
    private User user;

    @Column(name = "installation_id", nullable = false, length = 100)
    private String installationId;

    @Column(name = "push_token", nullable = false, length = 255)
    private String pushToken;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private Platform platform;

    @Column(name = "app_version", length = 40)
    private String appVersion;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "deactivation_reason", length = 40)
    private String deactivationReason;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PushDevice register(User user, String installationId, String pushToken, Platform platform,
                                      String appVersion, Instant now) {
        PushDevice device = new PushDevice();
        device.installationId = installationId;
        device.createdAt = now;
        device.refresh(user, pushToken, platform, appVersion, now);
        return device;
    }

    /** 앱 실행·로그인·토큰 변경 때마다 호출된다. 다른 계정이 같은 설치로 로그인하면 소유자를 바꾼다. */
    public void refresh(User user, String pushToken, Platform platform, String appVersion, Instant now) {
        this.user = user;
        this.pushToken = pushToken;
        this.platform = platform;
        this.appVersion = appVersion;
        this.active = true;
        this.deactivationReason = null;
        this.lastSeenAt = now;
        this.updatedAt = now;
    }

    public void deactivate(String reason, Instant now) {
        if (!active) {
            return;
        }
        this.active = false;
        this.deactivationReason = reason;
        this.updatedAt = now;
    }

    public enum Platform { IOS, ANDROID }

    /** 비활성 사유. 운영 로그·조회에서 원인을 구분한다. */
    public static final class Reasons {
        public static final String LOGGED_OUT = "LOGGED_OUT";
        public static final String DEVICE_NOT_REGISTERED = "DEVICE_NOT_REGISTERED";
        public static final String TOKEN_REASSIGNED = "TOKEN_REASSIGNED";
        public static final String STALE = "STALE";

        private Reasons() {
        }
    }
}
