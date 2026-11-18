package com.giut.server.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * 알림 한 건을 기기 한 대에 보내는 일(outbox). 알림을 만든 트랜잭션에서 PENDING으로 함께 저장되므로,
 * 원인 트랜잭션이 commit되면 서버가 바로 종료돼도 발송할 일이 DB에 남고, 롤백되면 함께 사라진다.
 *
 * <pre>
 * PENDING ─claim→ SENDING ─ticket ok→ SENT ─receipt ok→ DELIVERED
 *    ↑                │                  ├─receipt 없음(보관 기간 경과)→ UNCONFIRMED
 *    └─일시 오류·임대 만료┘                  └─receipt error→ FAILED (또는 재시도)
 *                     └─영구 오류·시도 소진·만료→ FAILED
 * </pre>
 * SENT·DELIVERED는 Expo·FCM·APNs가 받았다는 뜻이며, 사용자가 실제로 봤다는 뜻이 아니다.
 */
@Entity
@Table(name = "push_deliveries",
        uniqueConstraints = @UniqueConstraint(name = "uk_push_deliveries_notification_device",
                columnNames = {"notification_id", "device_id"}),
        indexes = {
                @Index(name = "idx_push_deliveries_status_next", columnList = "status, next_attempt_at"),
                @Index(name = "idx_push_deliveries_status_lease", columnList = "status, lease_expires_at"),
                @Index(name = "idx_push_deliveries_status_receipt", columnList = "status, receipt_check_at"),
                @Index(name = "idx_push_deliveries_completed", columnList = "completed_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDelivery {
    public static final int MAX_ERROR_MESSAGE_LENGTH = 300;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "notification_id", nullable = false, foreignKey = @ForeignKey(name = "fk_push_deliveries_notification"))
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "device_id", nullable = false, foreignKey = @ForeignKey(name = "fk_push_deliveries_device"))
    private PushDevice device;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "lease_token", length = 36)
    private String leaseToken;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "ticket_id", length = 100)
    private String ticketId;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "receipt_check_at")
    private Instant receiptCheckAt;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "error_message", length = MAX_ERROR_MESSAGE_LENGTH)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static PushDelivery pending(Notification notification, PushDevice device, Instant now) {
        PushDelivery delivery = new PushDelivery();
        delivery.notification = notification;
        delivery.device = device;
        delivery.status = Status.PENDING;
        delivery.nextAttemptAt = now;
        delivery.createdAt = now;
        delivery.updatedAt = now;
        return delivery;
    }

    /** SKIP LOCKED로 잠근 PENDING 행을 이번 실행에 넘긴다. 새 token이 이전 실행의 결과 저장을 무효화한다. */
    public String claim(Instant leaseExpiresAt, Instant now) {
        this.status = Status.SENDING;
        this.attemptCount++;
        this.leaseToken = UUID.randomUUID().toString();
        this.leaseExpiresAt = leaseExpiresAt;
        this.nextAttemptAt = null;
        this.updatedAt = now;
        return leaseToken;
    }

    public boolean isLeasedBy(String token) {
        return status == Status.SENDING && token != null && token.equals(leaseToken);
    }

    public void markSent(String ticketId, Instant receiptCheckAt, Instant now) {
        this.status = Status.SENT;
        this.ticketId = ticketId;
        this.sentAt = now;
        this.receiptCheckAt = receiptCheckAt;
        clearLease(now);
        clearError();
    }

    /** 시도가 남았으면 nextAttemptAt에 다시 대기시키고, 아니면 최종 실패로 끝낸다. */
    public void retryOrFail(int maxAttempts, Instant nextAttemptAt, String code, String message, Instant now) {
        if (attemptCount >= maxAttempts) {
            fail(code, message, now);
            return;
        }
        this.status = Status.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        this.ticketId = null;
        this.receiptCheckAt = null;
        this.errorCode = code;
        this.errorMessage = truncate(message);
        clearLease(now);
    }

    public void fail(String code, String message, Instant now) {
        this.status = Status.FAILED;
        this.errorCode = code;
        this.errorMessage = truncate(message);
        this.nextAttemptAt = null;
        this.receiptCheckAt = null;
        this.completedAt = now;
        clearLease(now);
    }

    public void markDelivered(Instant now) {
        this.status = Status.DELIVERED;
        this.receiptCheckAt = null;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void markUnconfirmed(Instant now) {
        this.status = Status.UNCONFIRMED;
        this.receiptCheckAt = null;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void postponeReceiptCheck(Instant receiptCheckAt, Instant now) {
        this.receiptCheckAt = receiptCheckAt;
        this.updatedAt = now;
    }

    private void clearLease(Instant now) {
        this.leaseToken = null;
        this.leaseExpiresAt = null;
        this.updatedAt = now;
    }

    private void clearError() {
        this.errorCode = null;
        this.errorMessage = null;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_ERROR_MESSAGE_LENGTH ? value : value.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    public enum Status { PENDING, SENDING, SENT, DELIVERED, UNCONFIRMED, FAILED }
}
