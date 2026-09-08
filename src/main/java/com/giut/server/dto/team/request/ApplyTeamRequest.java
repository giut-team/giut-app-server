package com.giut.server.dto.team.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "팀 참가 신청 요청")
public record ApplyTeamRequest(
        @Schema(description = "지원 메시지", example = "백엔드 개발로 참여하고 싶습니다.", nullable = true)
        @Size(max = 500, message = "지원 메시지는 500자 이하여야 합니다.")
        String message,

        @Schema(description = "지원서 질문별 답변 목록", example = "[{\"questionId\":1,\"answer\":\"서울시 공공데이터를 다뤄본 경험이 있습니다.\"}]")
        @NotNull(message = "지원서 답변 목록은 필수입니다. 질문이 없다면 빈 배열을 입력하세요.")
        List<@Valid TeamApplicationAnswerRequest> answers
) {
}
