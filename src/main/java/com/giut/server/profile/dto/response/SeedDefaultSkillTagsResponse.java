package com.giut.server.profile.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "기본 기술 스택 일괄 등록 결과")
public record SeedDefaultSkillTagsResponse(
        @Schema(description = "이번 요청에서 새로 등록한 기술 수", example = "40")
        int createdCount,
        @Schema(description = "기존 ID를 재사용한 기술 수. 누락된 역할 연결은 추가됩니다.", example = "0")
        int existingCount,
        @Schema(description = "기본 목록 순서의 기술 태그 ID. 기존 ID도 포함합니다.")
        List<Long> skillTagIds
) {
}

