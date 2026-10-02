package com.giut.server.service;

import com.giut.server.dto.chat.response.ChatMessageResponse;
import com.giut.server.entity.ChatMessage;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChatMessageBroadcasterTest {

    @Test
    void broadcastsToRoomTopic() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ChatMessageResponse response = new ChatMessageResponse(30L, 5L, 12L, "hello",
                ChatMessage.MessageType.TEXT, ChatMessage.Status.ACTIVE, null);

        new ChatMessageBroadcaster(messagingTemplate).broadcast(new ChatMessageEvent(response));

        verify(messagingTemplate).convertAndSend("/topic/chat-rooms/5", response);
    }
}
