package com.giut.server.dto.team.response;

import com.giut.server.entity.TeamApplication;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "팀 참가 신청 응답")
public record TeamApplicationResponse(
        @Schema(description = "참가 신청 ID", example = "1")
        Long applicationId,

        @Schema(description = "팀 ID", example = "1")
        Long teamId,

        @Schema(description = "신청자 사용자 ID", example = "15")
        Long userId,

        @Schema(description = "지원 메시지", example = "백엔드 개발로 참여하고 싶습니다.")
        String message,

        @Schema(description = "신청 상태", example = "PENDING")
        TeamApplication.Status status,

        @Schema(description = "신청 일시", example = "2026-09-08T10:40:00Z")
        Instant appliedAt,

        @Schema(description = "승인/거절 처리 일시", example = "2026-09-08T10:45:00Z", nullable = true)
        Instant decidedAt
) {

    public static TeamApplicationResponse from(TeamApplication application) {
        return new TeamApplicationResponse(
                application.getId(),
                application.getTeamId(),
                application.getUserId(),
                application.getMessage(),
                application.getStatus(),
                application.getAppliedAt(),
                application.getDecidedAt()
        );
    }
}
