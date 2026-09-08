package com.giut.server.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "duplicate_reviews") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DuplicateReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ingestion_id", nullable = false)
    private CompetitionIngestion ingestion;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "candidate_competition_id", nullable = false)
    private Competition candidateCompetition;

    @Enumerated(EnumType.STRING) @Column(name = "ai_result", nullable = false, length = 20)
    private AiResult aiResult;

    @Column(name = "ai_confidence", precision = 5, scale = 4)
    private BigDecimal aiConfidence;

    @Column(name = "ai_reason", columnDefinition = "text")
    private String aiReason;

    @Enumerated(EnumType.STRING) @Column(name = "review_status", nullable = false, length = 20)
    private Status reviewStatus;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public enum AiResult { SAME, DIFFERENT, UNCERTAIN }
    public enum Status { PENDING, MATCHED_EXISTING, CREATED_NEW }
}
