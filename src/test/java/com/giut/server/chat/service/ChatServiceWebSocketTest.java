package com.giut.server.chat.service;

import com.giut.server.chat.event.ChatMessageEvent;

import com.giut.server.chat.dto.request.SendChatMessageRequest;
import com.giut.server.chat.entity.ChatMessage;
import com.giut.server.chat.entity.ChatRoom;
import com.giut.server.chat.entity.ChatRoomMember;
import com.giut.server.chat.repository.ChatMessageRepository;
import com.giut.server.chat.repository.ChatRoomMemberRepository;
import com.giut.server.chat.repository.ChatRoomRepository;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceWebSocketTest {

    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock ChatMessageCreator chatMessageCreator;
    @Mock PersonalChatRoomCreator personalChatRoomCreator;
    @InjectMocks ChatService chatService;

    @Test
    void sendingMessagePublishesSavedMessage() {
        ChatRoom room = ChatRoom.createPersonalRoom(12L, 13L);
        ReflectionTestUtils.setField(room, "id", 5L);
        ChatRoomMember member = ChatRoomMember.join(5L, 12L);
        when(chatRoomRepository.findById(5L)).thenReturn(Optional.of(room));
        when(chatRoomMemberRepository.findByChatRoomIdAndUserId(5L, 12L))
                .thenReturn(Optional.of(member));
        ChatMessage message = ChatMessage.createText(
                5L,
                12L,
                "550e8400-e29b-41d4-a716-446655440000",
                "hello"
        );
        ReflectionTestUtils.setField(message, "id", 30L);
        when(chatMessageCreator.create(
                5L,
                12L,
                member.getId(),
                "550e8400-e29b-41d4-a716-446655440000",
                "hello"
        )).thenReturn(com.giut.server.chat.dto.response.ChatMessageResponse.from(message));

        var response = chatService.sendMessage(
                12L,
                5L,
                new SendChatMessageRequest(
                        "550e8400-e29b-41d4-a716-446655440000",
                        "hello"
                )
        );

        verify(chatMessageCreator).create(
                eq(5L),
                eq(12L),
                eq(member.getId()),
                eq("550e8400-e29b-41d4-a716-446655440000"),
                eq("hello")
        );
        assertEquals(30L, response.messageId());
        assertEquals("hello", response.content());
    }

    @Test
    void deletingMessagePublishesDeletedStatus() {
        ChatRoom room = ChatRoom.createPersonalRoom(12L, 13L);
        ReflectionTestUtils.setField(room, "id", 5L);
        ChatMessage message = ChatMessage.createText(5L, 12L, "hello");
        ReflectionTestUtils.setField(message, "id", 30L);
        when(chatRoomRepository.findById(5L)).thenReturn(Optional.of(room));
        when(chatRoomMemberRepository.findByChatRoomIdAndUserId(5L, 12L))
                .thenReturn(Optional.of(ChatRoomMember.join(5L, 12L)));
        when(chatMessageRepository.findById(30L)).thenReturn(Optional.of(message));

        chatService.deleteMessage(12L, 5L, 30L);

        ArgumentCaptor<ChatMessageEvent> event = ArgumentCaptor.forClass(ChatMessageEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(ChatMessage.Status.DELETED, event.getValue().message().status());
    }
}
