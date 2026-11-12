package com.giut.server.competition.dto.request;

import com.giut.server.competition.entity.CompetitionIngestionFailure;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

public record CompetitionIngestionFailureSearchRequest(
        @Schema(description = "데이터가 들어온 시각(배치 수신 시각) 시작, 포함. ISO-8601", example = "2026-11-12T00:00:00Z")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant from,

        @Schema(description = "데이터가 들어온 시각(배치 수신 시각) 끝, 미포함. ISO-8601", example = "2026-11-13T00:00:00Z")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant to,

        @Schema(description = "실패 유형. 생략하면 전체")
        CompetitionIngestionFailure.FailureType failureType,

        @Schema(description = "같은 배치 요청만 조회할 때 batchId")
        String batchId,

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
    public String normalizedBatchId() { return batchId == null || batchId.isBlank() ? null : batchId.trim(); }
}
