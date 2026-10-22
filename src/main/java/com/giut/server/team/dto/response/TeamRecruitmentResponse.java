package com.giut.server.team.dto.response;

import com.giut.server.team.entity.TeamRecruitment;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "팀 모집 분야 응답")
public record TeamRecruitmentResponse(
        @Schema(description = "모집 분야 ID", example = "1")
        Long recruitmentId,

        @Schema(description = "프로필 역할 코드", example = "BACKEND_DEVELOPER")
        String roleCode,

        @Schema(description = "필요 인원", example = "1")
        Short requiredCount,

        @Schema(description = "현재 충원된 인원", example = "1")
        long filledCount
) {

    public static TeamRecruitmentResponse from(TeamRecruitment recruitment) {
        return new TeamRecruitmentResponse(
                recruitment.getId(),
                recruitment.getRoleCode(),
                recruitment.getRequiredCount(),
                0
        );
    }

    public static TeamRecruitmentResponse from(TeamRecruitment recruitment, long filledCount) {
        return new TeamRecruitmentResponse(
                recruitment.getId(),
                recruitment.getRoleCode(),
                recruitment.getRequiredCount(),
                filledCount
        );
    }
}
