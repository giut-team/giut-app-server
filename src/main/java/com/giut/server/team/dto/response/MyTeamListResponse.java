package com.giut.server.team.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "내 소속 팀 목록")
public record MyTeamListResponse(
        List<MyTeamResponse> teams
) {
}
