package com.giut.server.entity;

import com.giut.server.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@Table(name = "profile_reports", indexes = {
        @Index(name = "idx_profile_reports_status_created", columnList = "status, created_at"),
        @Index(name = "idx_profile_reports_reporter_target_status",
                columnList = "reporter_user_id, reported_profile_user_id, status")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileReport extends BaseTimeEntity {

    public enum Reason {
        SPAM_ADVERTISING,
        FALSE_INFORMATION_IMPERSONATION,
        INAPPROPRIATE_BEHAVIOR,
        OTHER
    }

    public enum Status {
        PENDING,
        ACTIONED,
        DISMISSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_profile_reports_reporter"))
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_profile_user_id", referencedColumnName = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_profile_reports_profile"))
    private UserProfile reportedProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Reason reason;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "snapshot_nickname", nullable = false, length = 50)
    private String snapshotNickname;

    @Column(name = "snapshot_bio", columnDefinition = "text")
    private String snapshotBio;

    @Column(name = "snapshot_profile_image_url", length = 2048)
    private String snapshotProfileImageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id", foreignKey = @ForeignKey(name = "fk_profile_reports_reviewer"))
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    public static ProfileReport create(User reporter, UserProfile reportedProfile, Reason reason, String description) {
        ProfileReport report = new ProfileReport();
        report.reporter = reporter;
        report.reportedProfile = reportedProfile;
        report.reason = reason;
        report.description = description;
        report.status = Status.PENDING;
        report.snapshotNickname = reportedProfile.getUser().getNickname();
        report.snapshotBio = reportedProfile.getBio();
        report.snapshotProfileImageUrl = reportedProfile.getProfileImageUrl();
        report.createdAt = Instant.now();
        return report;
    }

    public void review(User reviewer, Status decision, String note) {
        if (status != Status.PENDING) {
            throw new ConflictException("이미 처리된 신고입니다.");
        }
        if (decision == Status.PENDING) {
            throw new IllegalArgumentException("검토 결과는 ACTIONED 또는 DISMISSED여야 합니다.");
        }
        reviewedBy = reviewer;
        reviewedAt = Instant.now();
        reviewNote = note;
        status = decision;
    }
}
