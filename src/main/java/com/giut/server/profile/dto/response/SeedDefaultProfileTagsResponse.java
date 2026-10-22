package com.giut.server.profile.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "기술 스택·관심 분야·활동 경험 기본 태그 일괄 등록 결과")
public record SeedDefaultProfileTagsResponse(
        @Schema(description = "새로 등록한 기술 스택 수. 기존 API와 동일하게 SKILL 기준입니다.", example = "0")
        int createdCount,
        @Schema(description = "재사용한 기술 스택 수. 기존 API와 동일하게 SKILL 기준입니다.", example = "40")
        int existingCount,
        @Schema(description = "기본 목록 순서의 기술 스택 ID")
        List<Long> skillTagIds,
        @Schema(description = "관심 분야(INTEREST) 등록 결과")
        TagTypeResult interestTags,
        @Schema(description = "활동 경험(EXPERIENCE) 등록 결과")
        TagTypeResult experienceTags
) {
    @Schema(name = "DefaultProfileTagTypeSeedResult", description = "유형별 기본 태그 등록 결과")
    public record TagTypeResult(
            @Schema(description = "새로 등록한 태그 수") int createdCount,
            @Schema(description = "기존 ID를 재사용한 태그 수") int existingCount,
            @Schema(description = "기본 목록 순서의 태그 ID") List<Long> tagIds
    ) {
    }
}
