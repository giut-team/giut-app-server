package com.giut.server.dto.team.response;

import com.giut.server.entity.TeamApplicationQuestion;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "팀 지원서 질문 응답")
public record TeamApplicationQuestionResponse(
        @Schema(description = "지원서 질문 ID", example = "1")
        Long questionId,

        @Schema(description = "지원서 질문", example = "이 팀에 지원한 이유를 알려주세요.")
        String question,

        @Schema(description = "필수 답변 여부", example = "true")
        boolean required,

        @Schema(description = "노출 순서", example = "1")
        int displayOrder
) {

    public static TeamApplicationQuestionResponse from(TeamApplicationQuestion question) {
        return new TeamApplicationQuestionResponse(
                question.getId(),
                question.getQuestion(),
                question.isRequired(),
                question.getDisplayOrder()
        );
    }
}
