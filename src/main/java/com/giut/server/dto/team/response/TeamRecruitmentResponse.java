package com.giut.server.dto.team.response;

import com.giut.server.entity.TeamRecruitment;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "팀 모집 분야 응답")
public record TeamRecruitmentResponse(
        @Schema(description = "모집 분야 ID", example = "1")
        Long recruitmentId,

        @Schema(description = "프로필 역할 코드", example = "BACKEND_DEVELOPER")
        String roleCode,

        @Schema(description = "필요 인원", example = "1")
        Short requiredCount
) {

    public static TeamRecruitmentResponse from(TeamRecruitment recruitment) {
        return new TeamRecruitmentResponse(
                recruitment.getId(),
                recruitment.getRoleCode(),
                recruitment.getRequiredCount()
        );
    }
}
