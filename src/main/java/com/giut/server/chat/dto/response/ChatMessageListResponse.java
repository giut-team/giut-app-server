package com.giut.server.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "채팅 메시지 목록 응답")
public record ChatMessageListResponse(
        @Schema(description = "채팅방 ID", example = "5")
        Long chatRoomId,

        @Schema(
                description = "다음 과거 메시지 조회에 사용할 커서. 다음 데이터가 없으면 null",
                example = "71"
        )
        Long nextCursor,

        @Schema(description = "조회 크기", example = "30")
        int size,

        @Schema(description = "다음 과거 메시지 존재 여부", example = "true")
        boolean hasNext,

        @Schema(description = "오래된 순서로 정렬된 메시지 목록")
        List<ChatMessageResponse> messages
) {
}