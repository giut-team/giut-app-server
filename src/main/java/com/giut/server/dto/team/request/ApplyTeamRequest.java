package com.giut.server.dto.team.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "팀 참가 신청 요청")
public record ApplyTeamRequest(
        @Schema(description = "지원 메시지", example = "백엔드 개발로 참여하고 싶습니다.", nullable = true)
        @Size(max = 500, message = "지원 메시지는 500자 이하여야 합니다.")
        String message
) {
}
