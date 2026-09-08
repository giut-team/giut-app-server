package com.giut.server.dto.chat.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "채팅 메시지 전송 요청")
public record SendChatMessageRequest(
        @Schema(description = "메시지 내용", example = "안녕하세요. 팀 채팅 테스트입니다.")
        @NotBlank(message = "메시지 내용은 필수입니다.")
        @Size(max = 2000, message = "메시지는 2000자 이하여야 합니다.")
        String content
) {
}
