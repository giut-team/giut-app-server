package com.giut.server.dto.team.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "공모전별 팀 목록")
public record TeamPageResponse(
        List<TeamSummaryResponse> teams,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
