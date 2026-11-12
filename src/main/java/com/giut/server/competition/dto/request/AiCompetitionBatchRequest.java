package com.giut.server.competition.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

/**
 * The AI client posts a bare JSON array; {"competitions": [...]} is also accepted.
 * Items stay as raw JSON so that one malformed item is recorded as a failure instead of rejecting the whole batch.
 */
@JsonDeserialize(using = AiCompetitionBatchRequestDeserializer.class)
public record AiCompetitionBatchRequest(
        @ArraySchema(arraySchema = @Schema(description = "검증이 완료된 공고 목록(1~20건). 공고마다 따로 저장하며 실패한 공고만 기록하고 나머지는 저장합니다."),
                schema = @Schema(implementation = AiCompetitionRequest.class))
        @NotEmpty(message = "공고는 한 건 이상이어야 합니다.")
        @Size(max = 20, message = "공고는 한 번에 최대 20건까지 보낼 수 있습니다.")
        List<JsonNode> competitions
) {}
