package com.giut.server.team.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "내 팀 참가 신청 목록")
public record MyTeamApplicationListResponse(List<TeamApplicationResponse> applications) {
}
