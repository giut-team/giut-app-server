package com.giut.server.competition.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "공모전 샘플 일괄 등록 결과")
public record SeedSampleCompetitionsResponse(
        @Schema(description = "이번 요청에서 새로 등록한 공모전 수", example = "10")
        int createdCount,
        @Schema(description = "같은 출처 URL이 이미 있어 등록을 건너뛴 공모전 수", example = "0")
        int skippedCount,
        @Schema(description = "샘플 목록 순서의 공모전 ID. 기존 공모전 ID도 포함합니다.", example = "[1,2,3,4,5,6,7,8,9,10]")
        List<Long> competitionIds
) {
}

