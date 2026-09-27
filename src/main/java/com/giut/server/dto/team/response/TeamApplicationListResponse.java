package com.giut.server.dto.team.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "팀 참가 신청 목록 응답")
public record TeamApplicationListResponse(
        @Schema(description = "팀 ID", example = "1")
        Long teamId,

        @Schema(description = "참가 신청 목록")
        List<TeamApplicationResponse> applications
) {
}
