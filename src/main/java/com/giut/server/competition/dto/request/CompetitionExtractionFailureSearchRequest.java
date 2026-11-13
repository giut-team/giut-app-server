package com.giut.server.competition.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

public record CompetitionExtractionFailureSearchRequest(
        @Schema(description = "실패 시각 시작, 포함. ISO-8601", example = "2026-11-12T00:00:00Z")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant from,

        @Schema(description = "실패 시각 끝, 미포함. ISO-8601", example = "2026-11-13T00:00:00Z")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant to,

        @Schema(description = "오류 코드. 생략하면 전체", example = "AI_UNAVAILABLE")
        @Size(max = 40, message = "errorCode는 40자 이하여야 합니다.")
        String errorCode,

        @Schema(description = "0부터 시작하는 페이지 번호", defaultValue = "0")
        @Min(value = 0, message = "page는 0 이상이어야 합니다.")
        Integer page,

        @Schema(description = "페이지 크기", defaultValue = "20")
        @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(value = 100, message = "size는 100 이하여야 합니다.")
        Integer size
) {
    public int pageOrDefault() { return page == null ? 0 : page; }
    public int sizeOrDefault() { return size == null ? 20 : size; }
    public String normalizedErrorCode() { return errorCode == null || errorCode.isBlank() ? null : errorCode.trim(); }
}
