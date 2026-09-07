package com.giut.server.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.List;

@Entity @Table(name = "competition_ingestions", indexes = @Index(name = "idx_ingestions_url_hash", columnList = "normalized_input_url_hash")) @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompetitionIngestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "competition_id") private Competition competition;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_id", nullable = false) private Source source;
    @Column(name = "submitted_by_user_id") private Long submittedByUserId;
    @Column(name = "external_id", length = 200) private String externalId;
    @Column(name = "input_url", columnDefinition = "text") private String inputUrl;
    @Column(name = "normalized_input_url", columnDefinition = "text") private String normalizedInputUrl;
    @Column(name = "normalized_input_url_hash", length = 64) private String normalizedInputUrlHash;
    @Enumerated(EnumType.STRING) @Column(name = "ingestion_status", nullable = false, length = 30) private Status ingestionStatus;
    @Enumerated(EnumType.STRING) @Column(name = "extraction_status", nullable = false, length = 20) private ExtractionStatus extractionStatus;
    @Enumerated(EnumType.STRING) @Column(name = "review_status", nullable = false, length = 20) private ReviewStatus reviewStatus;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "missing_fields", columnDefinition = "json") private List<String> missingFields;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "raw_payload", columnDefinition = "json") private String rawPayload;
    @Column(name = "reviewed_by_user_id") private Long reviewedByUserId;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "review_note", columnDefinition = "text") private String reviewNote;
    @Column(name = "ingested_at", nullable = false) private Instant ingestedAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (ingestedAt == null) ingestedAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public enum Status { RECEIVED, PARSED, DUPLICATE_REVIEW_PENDING, LINKED, REJECTED, FAILED }
    public enum ExtractionStatus { NOT_REQUIRED, PENDING, SUCCESS, PARTIAL, FAILED }
    public enum ReviewStatus { NOT_REQUIRED, PENDING, APPROVED, REJECTED }
}
