package com.giut.server.competition.dto.request;

import com.giut.server.competition.entity.Competition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record AdminCompetitionSearchRequest(
        @Schema(description = "0부터 시작하는 페이지 번호", defaultValue = "0")
        @Min(value = 0, message = "page는 0 이상이어야 합니다.")
        Integer page,

        @Schema(description = "페이지 크기", defaultValue = "10")
        @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(value = 20, message = "size는 20 이하여야 합니다.")
        Integer size,

        @Schema(description = "제목 또는 주최 기관 검색어")
        @Size(max = 100, message = "keyword는 100자 이하여야 합니다.")
        String keyword,

        Competition.Category category,

        @Schema(description = "게시 상태. 생략하면 ARCHIVED를 제외하며, ARCHIVED를 지정하면 보관된 건만 조회합니다.")
        Competition.PublicationStatus publicationStatus
) {
    public int pageOrDefault() { return page == null ? 0 : page; }
    public int sizeOrDefault() { return size == null ? 10 : size; }
    public String normalizedKeyword() { return keyword == null || keyword.isBlank() ? null : keyword.trim(); }
}
