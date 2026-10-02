package com.giut.server.dto.team.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "팀 참가 신청 승인 요청")
public record ApproveTeamApplicationRequest(
        @Schema(description = "합류할 모집 분야 코드", example = "BACKEND_DEVELOPER")
        @NotBlank(message = "합류 분야는 필수입니다.")
        String roleCode
) {
}
