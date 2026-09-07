package com.giut.server.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "duplicate_audit_candidates", uniqueConstraints = @UniqueConstraint(name = "uk_duplicate_candidate_pair", columnNames = {"competition_id_a", "competition_id_b"})) @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DuplicateAuditCandidate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "competition_id_a", nullable = false) private Competition competitionA;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "competition_id_b", nullable = false) private Competition competitionB;

    @Column(name = "match_score", precision = 5, scale = 4) private BigDecimal matchScore;

    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "match_basis", columnDefinition = "json") private String matchBasis;

    @Enumerated(EnumType.STRING) @Column(name = "ai_judgement", length = 20) private AiJudgement aiJudgement;

    @Enumerated(EnumType.STRING) @Column(name = "admin_decision", nullable = false, length = 20) private AdminDecision adminDecision = AdminDecision.PENDING;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "merged_into_competition_id") private Competition mergedIntoCompetition;

    @Column(name = "reviewed_by_user_id") private Long reviewedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    @Column(name = "reviewed_at") private Instant reviewedAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public enum AiJudgement { SAME, DIFFERENT, UNCERTAIN }
    public enum AdminDecision { PENDING, MERGE, KEEP_SEPARATE }
}
