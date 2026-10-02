package com.giut.server.service;

import com.giut.server.dto.chat.response.ChatMessageResponse;

public record ChatMessageEvent(ChatMessageResponse message) {
}
