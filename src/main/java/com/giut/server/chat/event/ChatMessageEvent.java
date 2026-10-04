package com.giut.server.chat.event;

import com.giut.server.chat.dto.response.ChatMessageResponse;

public record ChatMessageEvent(ChatMessageResponse message) {
}
