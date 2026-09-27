package com.giut.server.dto.team.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "팀 지원서 답변 요청")
public record TeamApplicationAnswerRequest(
        @Schema(description = "지원서 질문 ID", example = "1")
        @NotNull(message = "질문 ID는 필수입니다.")
        Long questionId,

        @Schema(description = "지원서 답변", example = "서울시 공공데이터를 다뤄본 경험이 있어서 주제와 잘 맞는다고 생각했습니다.")
        @NotBlank(message = "답변은 비어 있을 수 없습니다.")
        @Size(max = 2000, message = "답변은 2000자 이하여야 합니다.")
        String answer
) {
}
