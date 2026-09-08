package com.giut.server.dto.chat.response;

import com.giut.server.entity.ChatMessage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "채팅 메시지 응답")
public record ChatMessageResponse(
        @Schema(description = "메시지 ID", example = "1")
        Long messageId,

        @Schema(description = "채팅방 ID", example = "5")
        Long chatRoomId,

        @Schema(description = "보낸 사용자 ID", example = "12")
        Long senderId,

        @Schema(description = "메시지 내용", example = "안녕하세요. 팀 채팅 테스트입니다.")
        String content,

        @Schema(description = "메시지 타입", example = "TEXT")
        ChatMessage.MessageType messageType,

        @Schema(description = "메시지 상태", example = "ACTIVE")
        ChatMessage.Status status,

        @Schema(description = "생성일시", example = "2026-09-08T11:00:00Z")
        Instant createdAt
) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getChatRoomId(),
                message.getSenderId(),
                message.getContent(),
                message.getMessageType(),
                message.getStatus(),
                message.getCreatedAt()
        );
    }
}
