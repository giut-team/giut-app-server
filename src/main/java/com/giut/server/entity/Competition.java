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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Category category;

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

    public static Competition create(
            String title,
            Category category,
            String hostOrganization,
            String targetParticipants,
            String summary,
            Instant applicationStartAt,
            Instant applicationEndAt,
            PublicationStatus publicationStatus,
            VerificationStatus verificationStatus,
            User verifiedByUser
    ) {
        Competition competition = new Competition();
        competition.update(
                title,
                category,
                hostOrganization,
                targetParticipants,
                summary,
                applicationStartAt,
                applicationEndAt,
                publicationStatus,
                verificationStatus,
                verifiedByUser
        );
        return competition;
    }

    public void update(
            String title,
            Category category,
            String hostOrganization,
            String targetParticipants,
            String summary,
            Instant applicationStartAt,
            Instant applicationEndAt,
            PublicationStatus publicationStatus,
            VerificationStatus verificationStatus,
            User verifiedByUser
    ) {
        boolean verificationStatusChanged = this.verificationStatus != verificationStatus;
        this.title = title;
        this.category = category;
        this.hostOrganization = hostOrganization;
        this.targetParticipants = targetParticipants;
        this.summary = summary;
        this.applicationStartAt = applicationStartAt;
        this.applicationEndAt = applicationEndAt;
        this.publicationStatus = publicationStatus;
        this.verificationStatus = verificationStatus;
        if (isVerified(verificationStatus)) {
            if (verificationStatusChanged || this.verifiedAt == null) {
                this.verifiedByUser = verifiedByUser;
                this.verifiedAt = Instant.now();
            }
        } else {
            this.verifiedByUser = null;
            this.verifiedAt = null;
        }
    }

    private boolean isVerified(VerificationStatus verificationStatus) {
        return verificationStatus == VerificationStatus.AUTO_VERIFIED
                || verificationStatus == VerificationStatus.MANUALLY_VERIFIED;
    }

    @Getter
    @RequiredArgsConstructor
    public enum Category {
        PLANNING_IDEA("기획/아이디어"),
        ADVERTISING_MARKETING("광고/마케팅"),
        PAPER_REPORT("논문/리포트"),
        VIDEO_UCC_PHOTO("영상/UCC/사진"),
        DESIGN_CHARACTER_WEBTOON("디자인/캐릭터/웹툰"),
        WEB_MOBILE_IT("웹/모바일/IT"),
        GAME_SOFTWARE("게임/소프트웨어"),
        SCIENCE_ENGINEERING("과학/공학"),
        LITERATURE_WRITING_SCENARIO("문학/글/시나리오"),
        ARCHITECTURE_CONSTRUCTION_INTERIOR("건축/건설/인테리어"),
        NAMING_SLOGAN("네이밍/슬로건"),
        ENTERTAINMENT_ART_MUSIC("예체능/미술/음악"),
        ETC("기타");

        private final String displayName;
    }

    public enum PublicationStatus { DRAFT, PUBLISHED, REJECTED, ARCHIVED }
    public enum VerificationStatus { AUTO_VERIFIED, UNVERIFIED, MANUAL_REVIEW_PENDING, MANUALLY_VERIFIED, REJECTED }
}
