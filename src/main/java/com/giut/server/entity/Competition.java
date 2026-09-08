package com.giut.server.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "competitions") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Competition extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 50)
    private String category;

    @Column(name = "host_organization", length = 150)
    private String hostOrganization;

    @Column(name = "target_participants", columnDefinition = "text")
    private String targetParticipants;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "application_start_at")
    private Instant applicationStartAt;

    @Column(name = "application_end_at")
    private Instant applicationEndAt;

    @Enumerated(EnumType.STRING) @Column(name = "publication_status", nullable = false, length = 20)
    private PublicationStatus publicationStatus;

    @Enumerated(EnumType.STRING) @Column(name = "verification_status", nullable = false, length = 30)
    private VerificationStatus verificationStatus;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_user_id", foreignKey = @ForeignKey(name = "fk_competitions_verified_by_user"))
    private User verifiedByUser;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    public enum PublicationStatus { DRAFT, PUBLISHED, REJECTED, ARCHIVED }
    public enum VerificationStatus { AUTO_VERIFIED, UNVERIFIED, MANUAL_REVIEW_PENDING, MANUALLY_VERIFIED, REJECTED }
}
