package com.giut.server.dto.team.response;

import com.giut.server.entity.TeamApplicationAnswer;
import com.giut.server.entity.TeamApplicationQuestion;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "팀 지원서 답변 응답")
public record TeamApplicationAnswerResponse(
        @Schema(description = "지원서 질문 ID", example = "1")
        Long questionId,

        @Schema(description = "지원서 질문", example = "이 팀에 지원한 이유를 알려주세요.")
        String question,

        @Schema(description = "지원서 답변", example = "서울시 공공데이터를 다뤄본 경험이 있어서 주제와 잘 맞는다고 생각했습니다.")
        String answer,

        @Schema(description = "노출 순서", example = "1")
        int displayOrder
) {

    public static TeamApplicationAnswerResponse of(
            TeamApplicationQuestion question,
            TeamApplicationAnswer answer
    ) {
        return new TeamApplicationAnswerResponse(
                question.getId(),
                question.getQuestion(),
                answer.getAnswer(),
                question.getDisplayOrder()
        );
    }
}
