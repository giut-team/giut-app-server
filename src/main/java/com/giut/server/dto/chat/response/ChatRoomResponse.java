package com.giut.server.dto.chat.response;

import com.giut.server.entity.ChatRoom;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "채팅방 응답")
public record ChatRoomResponse(
        @Schema(description = "채팅방 ID", example = "5")
        Long chatRoomId,

        @Schema(description = "채팅방 타입", example = "PERSONAL")
        ChatRoom.Type type,

        @Schema(description = "채팅방 상태", example = "ACTIVE")
        ChatRoom.Status status,

        @Schema(description = "상대 사용자 ID", example = "15")
        Long targetUserId,

        @Schema(description = "새로 생성된 채팅방 여부", example = "true")
        boolean created,

        @Schema(description = "생성일시", example = "2026-09-08T11:10:00Z")
        Instant createdAt
) {

    public static ChatRoomResponse of(ChatRoom chatRoom, Long targetUserId, boolean created) {
        return new ChatRoomResponse(
                chatRoom.getId(),
                chatRoom.getType(),
                chatRoom.getStatus(),
                targetUserId,
                created,
                chatRoom.getCreatedAt()
        );
    }
}
