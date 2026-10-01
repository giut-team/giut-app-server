package com.giut.server.dto.profile.response;

import com.giut.server.entity.ProfileReport;

import java.time.Instant;

public record AdminProfileReportDetailResponse(
        Long id,
        Long reporterUserId,
        Long reportedProfileUserId,
        ProfileReport.Reason reason,
        String description,
        ProfileReport.Status status,
        String snapshotNickname,
        String snapshotBio,
        String snapshotProfileImageUrl,
        Instant createdAt,
        Long reviewedByUserId,
        Instant reviewedAt,
        String reviewNote
) {
    public static AdminProfileReportDetailResponse from(ProfileReport report) {
        return new AdminProfileReportDetailResponse(
                report.getId(),
                report.getReporter().getId(),
                report.getReportedProfile().getUserId(),
                report.getReason(),
                report.getDescription(),
                report.getStatus(),
                report.getSnapshotNickname(),
                report.getSnapshotBio(),
                report.getSnapshotProfileImageUrl(),
                report.getCreatedAt(),
                report.getReviewedBy() == null ? null : report.getReviewedBy().getId(),
                report.getReviewedAt(),
                report.getReviewNote()
        );
    }
}
