package com.giut.server.team.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "팀 참가 신청 거절 요청")
public record RejectTeamApplicationRequest(
        @Schema(description = "거절 사유", example = "이번 모집 분야와 경험이 맞지 않습니다.", nullable = true)
        @Size(max = 500, message = "거절 사유는 500자 이하여야 합니다.")
        String reason
) {
}
