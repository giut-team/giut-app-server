package com.giut.server.dto.team.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "팀 지원서 질문 생성 요청")
public record CreateTeamQuestionRequest(
        @Schema(description = "지원서 질문", example = "이 팀에 지원한 이유를 알려주세요.")
        @NotBlank(message = "지원서 질문은 비어 있을 수 없습니다.")
        @Size(max = 300, message = "지원서 질문은 300자 이하여야 합니다.")
        String question,

        @Schema(description = "필수 답변 여부", example = "true")
        @NotNull(message = "필수 답변 여부는 필수입니다.")
        Boolean required
) {
}
