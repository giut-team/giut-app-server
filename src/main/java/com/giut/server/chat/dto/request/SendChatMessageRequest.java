package com.giut.server.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "채팅 메시지 전송 요청")
public record SendChatMessageRequest(
        @Schema(
                description = "클라이언트가 메시지마다 생성하는 UUID. 같은 메시지를 재시도할 때는 같은 값을 사용합니다.",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        @NotBlank(message = "클라이언트 메시지 ID는 필수입니다.")
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "클라이언트 메시지 ID는 UUID 형식이어야 합니다."
        )
        String clientMessageId,

        @Schema(description = "메시지 내용", example = "안녕하세요. 팀 채팅 테스트입니다.")
        @NotBlank(message = "메시지 내용은 필수입니다.")
        @Size(max = 2000, message = "메시지는 2000자 이하여야 합니다.")
        String content
) {
}
