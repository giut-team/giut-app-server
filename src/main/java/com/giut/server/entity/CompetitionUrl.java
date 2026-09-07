package com.giut.server.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "competition_urls") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompetitionUrl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "competition_id", nullable = false) private Competition competition;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "first_seen_ingestion_id") private CompetitionIngestion firstSeenIngestion;
    @Enumerated(EnumType.STRING) @Column(name = "url_type", nullable = false, length = 20) private Type urlType;
    @Column(nullable = false, columnDefinition = "text") private String url;
    @Column(name = "normalized_url", nullable = false, columnDefinition = "text") private String normalizedUrl;
    @Column(name = "normalized_url_hash", nullable = false, unique = true, length = 64) private String normalizedUrlHash;
    @Column(name = "is_primary", nullable = false) private boolean primary;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public enum Type { RECRUITMENT, OFFICIAL, SOURCE_ORIGINAL }
}
