package com.giut.server.competition.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

public record CompetitionIngestionFailureSummaryRequest(
        @Schema(description = "데이터가 들어온 시각(배치 수신 시각) 시작, 포함. 생략하면 처음부터. ISO-8601", example = "2026-11-12T00:00:00Z")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant from,

        @Schema(description = "데이터가 들어온 시각(배치 수신 시각) 끝, 미포함. 생략하면 지금까지. ISO-8601", example = "2026-11-13T00:00:00Z")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant to
) {
}
