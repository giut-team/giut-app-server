package com.giut.server.dto.chat.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "채팅방 목록 응답")
public record ChatRoomListResponse(
        @Schema(description = "채팅방 목록")
        List<ChatRoomResponse> chatRooms
) {
}
