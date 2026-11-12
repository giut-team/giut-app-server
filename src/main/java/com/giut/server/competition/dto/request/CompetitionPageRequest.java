package com.giut.server.competition.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.giut.server.competition.entity.Competition;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.AssertTrue;

public record CompetitionPageRequest(
        @Schema(description = "0부터 시작하는 페이지 번호", example = "0", defaultValue = "0")
        @Min(value = 0, message = "page는 0 이상이어야 합니다.") Integer page,
        @Schema(description = "페이지 크기", example = "10", defaultValue = "10")
        @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(value = 20, message = "size는 20 이하여야 합니다.") Integer size,
        @Schema(description = "공모전 카테고리", example = "WEB_MOBILE_IT") Competition.Category category
) {
    public int pageOrDefault() { return page == null ? 0 : page; }
    public int sizeOrDefault() { return size == null ? 10 : size; }

    @AssertTrue(message = "요청한 페이지가 너무 큽니다.")
    @Schema(hidden = true)
    public boolean isOffsetSupported() {
        return (long) pageOrDefault() * sizeOrDefault() <= Integer.MAX_VALUE;
    }
}
