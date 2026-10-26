package com.giut.server.competition.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "공모전 샘플 일괄 등록 결과")
public record SeedSampleCompetitionsResponse(
        @Schema(description = "이번 요청에서 새로 등록한 공모전 수", example = "10")
        int createdCount,
        @Schema(description = "호환성을 위해 유지하는 필드. 호출마다 새 배치를 등록하므로 항상 0입니다.", example = "0")
        int skippedCount,
        @Schema(description = "이번 요청에서 새로 등록한 공모전 10개의 ID (샘플 목록 순서)", example = "[1,2,3,4,5,6,7,8,9,10]")
        List<Long> competitionIds
) {
}

