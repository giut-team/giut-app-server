package com.giut.server.dto.chat.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "개인 채팅방 생성 요청")
public record CreatePersonalChatRoomRequest(
        @Schema(description = "상대 사용자 ID", example = "15")
        @NotNull(message = "상대 사용자 ID는 필수입니다.")
        Long targetUserId
) {
}
