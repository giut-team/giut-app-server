package com.giut.server.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채팅방 읽지 않은 메시지 수 응답")
public record ChatRoomUnreadCountResponse(
        @Schema(description = "채팅방 ID", example = "5")
        Long chatRoomId,

        @Schema(description = "읽지 않은 메시지 수", example = "3")
        long unreadCount
) {
}
