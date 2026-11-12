package com.giut.server.competition.entity;

import com.giut.server.global.entity.BaseTimeEntity;
import com.giut.server.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Objects;

@Entity @Table(name = "competitions", uniqueConstraints =
        @UniqueConstraint(name = "uk_competitions_submission_url_hash", columnNames = "submission_source_url_hash"),
        indexes = {
        @Index(name = "idx_competitions_status_id", columnList = "publication_status, id"),
        @Index(name = "idx_competitions_status_app_end_at", columnList = "publication_status, application_end_at")
}) @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Competition extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Category category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "activity_type", length = 30, nullable = false)
    @ColumnDefault("'COMPETITION'")
    private ActivityType activityType = ActivityType.COMPETITION;

    @Column(name = "host_organization", length = 150)
    private String hostOrganization;

    @Column(name = "target_participants", columnDefinition = "text")
    private String targetParticipants;

    @Column(name = "prize_info", columnDefinition = "text")
    private String prizeInfo;

    @Column(name = "total_prize_amount")
    private Long totalPrizeAmount;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "application_start_at")
    private Instant applicationStartAt;

    @Column(name = "application_end_at")
    private Instant applicationEndAt;

    @Column(name = "is_for_enrolled_only")
    private Boolean isForEnrolledOnly;

    @Column(name = "is_team")
    private Boolean isTeam;

    @Enumerated(EnumType.STRING) @Column(name = "publication_status", nullable = false, length = 20)
    private PublicationStatus publicationStatus;

    @Enumerated(EnumType.STRING) @Column(name = "verification_status", nullable = false, length = 30)
    private VerificationStatus verificationStatus;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_user_id", foreignKey = @ForeignKey(name = "fk_competitions_verified_by_user"))
    private User verifiedByUser;

    @Column(name = "submission_source_url", columnDefinition = "text", updatable = false)
    private String submissionSourceUrl;

    @Column(name = "submission_source_url_hash", length = 64, updatable = false)
    private String submissionSourceUrlHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_user_id", updatable = false,
            foreignKey = @ForeignKey(name = "fk_competitions_submitted_by_user"))
    private User submittedByUser;

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
            // 검증 상태가 그대로 유지된 채 다른 필드만 수정되는 경우에는 verifiedAt을 다시 찍지 않는다
            // (최초 검증 시각을 보존). 상태가 실제로 바뀌었을 때만 검증 시각·검증자를 갱신한다.
            if (verificationStatusChanged || this.verifiedAt == null) {
                this.verifiedByUser = verifiedByUser;
                this.verifiedAt = Instant.now();
            }
        } else {
            this.verifiedByUser = null;
            this.verifiedAt = null;
        }
    }

    public void recordSubmission(User submitter, String sourceUrl, String sourceUrlHash) {
        if (submissionSourceUrlHash != null || publicationStatus != PublicationStatus.DRAFT
                || verificationStatus != VerificationStatus.MANUAL_REVIEW_PENDING) {
            throw new IllegalStateException("검토 대기 공모전에만 제출 정보를 기록할 수 있습니다.");
        }
        submittedByUser = Objects.requireNonNull(submitter);
        submissionSourceUrl = Objects.requireNonNull(sourceUrl);
        submissionSourceUrlHash = Objects.requireNonNull(sourceUrlHash);
    }

    public boolean isMemberSubmission() {
        return submissionSourceUrlHash != null;
    }

    public void reviewSubmission(boolean approved, User reviewer) {
        if (!isMemberSubmission() || publicationStatus != PublicationStatus.DRAFT
                || verificationStatus != VerificationStatus.MANUAL_REVIEW_PENDING) {
            throw new IllegalStateException("검토 대기 중인 회원 제출만 처리할 수 있습니다.");
        }
        if (approved) {
            verificationStatus = VerificationStatus.MANUALLY_VERIFIED;
            verifiedAt = Instant.now();
            verifiedByUser = Objects.requireNonNull(reviewer);
            publish();
        } else {
            verificationStatus = VerificationStatus.REJECTED;
            publicationStatus = PublicationStatus.REJECTED;
        }
    }

    public void publish() {
        if (publicationStatus != PublicationStatus.DRAFT) {
            throw new IllegalArgumentException("임시 저장 상태의 공모전만 공개 승인할 수 있습니다.");
        }
        this.publicationStatus = PublicationStatus.PUBLISHED;
    }

    public void changeActivityType(ActivityType activityType) {
        this.activityType = Objects.requireNonNull(activityType, "활동 유형은 필수입니다.");
    }

    public void changePrizeInfo(String prizeInfo) {
        this.prizeInfo = prizeInfo;
    }

    public void changeTotalPrizeAmount(Long totalPrizeAmount) {
        this.totalPrizeAmount = totalPrizeAmount;
    }

    public void changeIsForEnrolledOnly(Boolean isForEnrolledOnly) {
        this.isForEnrolledOnly = isForEnrolledOnly;
    }

    public void changeIsTeam(Boolean isTeam) {
        this.isTeam = isTeam;
    }

    /** Preserves dependent teams, scraps and review history while removing public visibility. */
    public void archive() {
        this.publicationStatus = PublicationStatus.ARCHIVED;
    }

    /** Verification is independent of publication and recruitment status. */
    public boolean isVerified() {
        return isVerified(verificationStatus);
    }

    private boolean isVerified(VerificationStatus verificationStatus) {
        return verificationStatus == VerificationStatus.AUTO_VERIFIED
                || verificationStatus == VerificationStatus.MANUALLY_VERIFIED;
    }

    @Getter
    @RequiredArgsConstructor
    public enum ActivityType {
        COMPETITION("공모전"), CONTEST("경진대회"), HACKATHON("해커톤"),
        SUPPORTERS("서포터즈"), VOLUNTEERING("봉사"), STARTUP("창업"),
        EDUCATION_PROGRAM("교육/프로그램"), OVERSEAS_EXPLORATION("해외탐방"),
        EVENT_NETWORKING("행사/네트워킹"), ETC("기타"), NOT_ACTIVITY("참여 가능한 활동이 아님");

        private final String displayName;

    }

    @Getter
    @RequiredArgsConstructor
    public enum Category {
        PLANNING_IDEA("기획/아이디어"),
        ADVERTISING_MARKETING("광고/마케팅"),
        AI_DATA("AI/데이터"),
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
