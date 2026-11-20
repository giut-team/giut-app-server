package com.giut.server.team.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.giut.server.team.entity.TeamApplication;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "팀 참가 신청 응답")
public record TeamApplicationResponse(
        @Schema(description = "참가 신청 ID", example = "1")
        Long applicationId,

        @Schema(description = "팀 ID", example = "1")
        Long teamId,

        @Schema(description = "신청자 사용자 ID", example = "15")
        Long userId,

        @Schema(description = "지원 분야 코드", example = "BACKEND_DEVELOPER", nullable = true)
        String roleCode,

        @Schema(description = "승인된 합류 분야 코드", example = "BACKEND_DEVELOPER", nullable = true)
        String assignedRoleCode,

        @Schema(description = "거절 사유", nullable = true)
        String rejectionReason,

        @Schema(description = "지원 메시지", example = "백엔드 개발로 참여하고 싶습니다.")
        String message,

        @Schema(description = "신청 상태", example = "PENDING")
        TeamApplication.Status status,

        @Schema(description = "신청 일시", example = "2026-09-08T10:40:00Z")
        Instant appliedAt,

        @Schema(description = "승인/거절 처리 일시", example = "2026-09-08T10:45:00Z", nullable = true)
        Instant decidedAt,

        @Schema(description = "지원서 답변 목록")
        List<TeamApplicationAnswerResponse> answers,

        @Schema(description = "내 지원 목록에 표시할 팀 요약")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        AppliedTeamResponse team,

        @Schema(description = "팀장용 지원 목록에 표시하는 활성 지원자의 닉네임")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String applicantNickname
) {

    public static TeamApplicationResponse of(
            TeamApplication application,
            List<TeamApplicationAnswerResponse> answers
    ) {
        return of(application, answers, null, null);
    }

    public static TeamApplicationResponse of(
            TeamApplication application,
            List<TeamApplicationAnswerResponse> answers,
            AppliedTeamResponse team,
            String applicantNickname
    ) {
        return new TeamApplicationResponse(
                application.getId(),
                application.getTeamId(),
                application.getUserId(),
                application.getRoleCode(),
                application.getAssignedRoleCode(),
                application.getRejectionReason(),
                application.getMessage(),
                application.getStatus(),
                application.getAppliedAt(),
                application.getDecidedAt(),
                answers,
                team,
                applicantNickname
        );
    }
}
