package com.giut.server.chat.event;

import com.giut.server.chat.dto.response.ChatMessageResponse;
import com.giut.server.chat.entity.ChatMessage;
import com.giut.server.chat.entity.ChatRoomMember;
import com.giut.server.chat.repository.ChatRoomMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageBroadcasterTest {

    @Test
    void broadcastsOnlyToCurrentParticipants() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ChatRoomMemberRepository members = mock(ChatRoomMemberRepository.class);
        ChatMessageResponse response = new ChatMessageResponse(30L, 5L, 12L, "hello",
                ChatMessage.MessageType.TEXT, ChatMessage.Status.ACTIVE, null);
        when(members.findAllByChatRoomIdAndLeftAtIsNull(5L))
                .thenReturn(List.of(ChatRoomMember.join(5L, 12L), ChatRoomMember.join(5L, 13L)));

        new ChatMessageBroadcaster(messagingTemplate, members).broadcast(new ChatMessageEvent(response));

        verify(messagingTemplate).convertAndSendToUser("12", "/queue/chat-rooms/5", response);
        verify(messagingTemplate).convertAndSendToUser("13", "/queue/chat-rooms/5", response);
    }

    @Test
    void doesNotBroadcastToFormerParticipant() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ChatRoomMemberRepository members = mock(ChatRoomMemberRepository.class);
        ChatMessageResponse response = new ChatMessageResponse(30L, 5L, 12L, "hello",
                ChatMessage.MessageType.TEXT, ChatMessage.Status.ACTIVE, null);
        when(members.findAllByChatRoomIdAndLeftAtIsNull(5L))
                .thenReturn(List.of(ChatRoomMember.join(5L, 12L)));

        new ChatMessageBroadcaster(messagingTemplate, members).broadcast(new ChatMessageEvent(response));

        verify(messagingTemplate).convertAndSendToUser("12", "/queue/chat-rooms/5", response);
        verify(messagingTemplate, never()).convertAndSendToUser("13", "/queue/chat-rooms/5", response);
    }
}
