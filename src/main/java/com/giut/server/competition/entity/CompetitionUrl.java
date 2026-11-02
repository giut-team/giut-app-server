package com.giut.server.competition.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "competition_urls", uniqueConstraints = {
        @UniqueConstraint(name = "uk_competition_urls_official_hash", columnNames = "official_url_hash"),
        @UniqueConstraint(name = "uk_competition_urls_competition_hash", columnNames = {"competition_id", "normalized_url_hash"})
}) @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompetitionUrl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "competition_id", nullable = false)
    private Competition competition;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "first_seen_ingestion_id")
    private CompetitionIngestion firstSeenIngestion;

    @Enumerated(EnumType.STRING) @Column(name = "url_type", nullable = false, length = 20)
    private Type urlType;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "normalized_url", nullable = false, columnDefinition = "text")
    private String normalizedUrl;

    @Column(name = "normalized_url_hash", nullable = false, length = 64)
    private String normalizedUrlHash;

    @Column(name = "official_url_hash", insertable = false, updatable = false,
            columnDefinition = "varchar(64) generated always as (case when url_type = 'OFFICIAL' then normalized_url_hash else null end)")
    private String officialUrlHash;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CompetitionUrl create(
            Competition competition,
            Type urlType,
            String url,
            String normalizedUrl,
            String normalizedUrlHash,
            boolean primary
    ) {
        CompetitionUrl competitionUrl = new CompetitionUrl();
        competitionUrl.competition = competition;
        competitionUrl.urlType = urlType;
        competitionUrl.url = url;
        competitionUrl.normalizedUrl = normalizedUrl;
        competitionUrl.normalizedUrlHash = normalizedUrlHash;
        competitionUrl.primary = primary;
        return competitionUrl;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public enum Type { RECRUITMENT, OFFICIAL, SOURCE_ORIGINAL }
}
