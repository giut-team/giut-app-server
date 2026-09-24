package com.giut.server.dto.competition.request;

import com.giut.server.entity.Competition;
import com.giut.server.dto.competition.response.CompetitionRecruitmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record CompetitionSearchRequest(
        @Schema(description = "0부터 시작하는 페이지 번호", example = "0", defaultValue = "0")
        @Min(value = 0, message = "page는 0 이상이어야 합니다.")
        Integer page,

        @Schema(description = "페이지 크기", example = "10", defaultValue = "10")
        @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(value = 20, message = "size는 20 이하여야 합니다.")
        Integer size,

        @Schema(description = "제목 또는 주최 기관 검색어", example = "데이터")
        @Size(max = 100, message = "keyword는 100자 이하여야 합니다.")
        String keyword,

        @Schema(description = "공모전 카테고리", example = "WEB_MOBILE_IT")
        Competition.Category category,

        @Schema(description = "모집 상태", example = "OPEN")
        CompetitionRecruitmentStatus status
) {

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? 10 : size;
    }

    public String normalizedKeyword() {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }
}
