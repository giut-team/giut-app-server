package com.giut.server.dto.team.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "팀 모집 분야 요청")
public record CreateTeamRecruitmentRequest(
        @Schema(description = "프로필 역할 코드", example = "BACKEND_DEVELOPER")
        @NotBlank(message = "모집 분야는 필수입니다.")
        String roleCode,

        @Schema(description = "필요 인원", example = "1")
        @NotNull(message = "필요 인원은 필수입니다.")
        @Min(value = 1, message = "필요 인원은 1명 이상이어야 합니다.")
        @Max(value = 20, message = "필요 인원은 20명 이하여야 합니다.")
        Short requiredCount
) {
}
