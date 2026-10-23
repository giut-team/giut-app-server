package com.giut.server.profile.dto.response;

import com.giut.server.profile.entity.ProfileReport;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "현재 로그인 사용자가 해당 프로필에 제출한 처리 대기 신고")
public record PendingProfileReportResponse(
        @Schema(description = "본인의 PENDING 신고 존재 여부")
        boolean pending,
        @Schema(description = "본인의 처리 대기 신고. 없으면 null", nullable = true)
        Report report
) {
    public static PendingProfileReportResponse none() {
        return new PendingProfileReportResponse(false, null);
    }

    public static PendingProfileReportResponse from(ProfileReport report) {
        return new PendingProfileReportResponse(true, new Report(
                report.getId(), report.getReportedProfile().getUserId(), report.getStatus(),
                report.getReason(), report.getDescription(), report.getCreatedAt()));
    }

    public record Report(
            Long id,
            Long userId,
            ProfileReport.Status status,
            ProfileReport.Reason reason,
            @Schema(nullable = true) String description,
            Instant createdAt
    ) {
    }
}
