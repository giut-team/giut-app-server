package com.giut.server.profile.dto.response;

import com.giut.server.profile.entity.ProfileReport;

import java.time.Instant;

public record AdminProfileReportSummaryResponse(
        Long id,
        Long reporterUserId,
        Long reportedProfileUserId,
        ProfileReport.Reason reason,
        ProfileReport.Status status,
        Instant createdAt
) {
    public static AdminProfileReportSummaryResponse from(ProfileReport report) {
        return new AdminProfileReportSummaryResponse(
                report.getId(),
                report.getReporter().getId(),
                report.getReportedProfile().getUserId(),
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}
