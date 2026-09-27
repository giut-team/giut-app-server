package com.giut.server.dto.team.response;

import com.giut.server.entity.TeamApplication;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "팀 참가 신청 승인 응답")
public record ApproveTeamApplicationResponse(
        @Schema(description = "참가 신청 ID", example = "1")
        Long applicationId,

        @Schema(description = "팀 ID", example = "1")
        Long teamId,

        @Schema(description = "승인된 사용자 ID", example = "15")
        Long userId,

        @Schema(description = "신청 상태", example = "APPROVED")
        TeamApplication.Status status,

        @Schema(description = "팀원 ID", example = "3")
        Long teamMemberId
) {
}
