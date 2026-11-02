package com.giut.server.competition.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AiCompetitionBatchRequest(
        @Schema(description = "검증이 완료된 공고 목록. 한 건이라도 실패하면 전체 저장이 취소됩니다.")
        @NotEmpty(message = "공고는 한 건 이상이어야 합니다.")
        @Size(max = 20, message = "공고는 한 번에 최대 20건까지 보낼 수 있습니다.")
        List<@NotNull @Valid AiCompetitionRequest> competitions
) {}
