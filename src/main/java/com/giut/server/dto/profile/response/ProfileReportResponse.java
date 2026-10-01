package com.giut.server.dto.profile.response;

import com.giut.server.entity.ProfileReport;

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
