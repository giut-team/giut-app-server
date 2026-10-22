package com.giut.server.profile.dto.response;

import com.giut.server.profile.entity.ProfileReport;

import java.time.Instant;

public record ProfileReportResponse(
        Long id,
        ProfileReport.Status status,
        Instant createdAt
) {
    public static ProfileReportResponse from(ProfileReport report) {
        return new ProfileReportResponse(report.getId(), report.getStatus(), report.getCreatedAt());
    }
}
