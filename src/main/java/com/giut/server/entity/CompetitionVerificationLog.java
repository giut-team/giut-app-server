package com.giut.server.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity @Table(name = "competition_verification_logs") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompetitionVerificationLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "competition_id", nullable = false) private Competition competition;

    @Enumerated(EnumType.STRING) @Column(name = "from_status", length = 30) private Competition.VerificationStatus fromStatus;

    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, length = 30) private Competition.VerificationStatus toStatus;

    @Enumerated(EnumType.STRING) @Column(name = "verification_method", nullable = false, length = 20) private Method verificationMethod;

    @Column(name = "actor_user_id") private Long actorUserId;

    @Column(columnDefinition = "text") private String reason;

    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "json") private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public enum Method { SOURCE_TRUST, AUTO_EXTRACTION, ADMIN_REVIEW }
}
