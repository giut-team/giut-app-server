package com.giut.server.dto.team.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "팀원 목록 응답")
public record TeamMemberListResponse(
        @Schema(description = "팀 ID", example = "1")
        Long teamId,

        @Schema(description = "팀원 목록")
        List<TeamMemberResponse> members
) {
}
