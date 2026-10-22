package com.giut.server.chat.security;

import com.giut.server.chat.entity.ChatRoom;
import com.giut.server.chat.repository.ChatRoomMemberRepository;
import com.giut.server.chat.repository.ChatRoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSubscriptionInterceptorTest {

    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ChatRoomMemberRepository chatRoomMemberRepository;
    @InjectMocks ChatSubscriptionInterceptor interceptor;

    @Test
    void allowsActiveParticipantToSubscribe() {
        ChatRoom room = ChatRoom.createPersonalRoom();
        when(chatRoomRepository.findById(5L)).thenReturn(Optional.of(room));
        when(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(5L, 12L))
                .thenReturn(true);

        assertDoesNotThrow(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE,
                "/user/queue/chat-rooms/5", () -> "12"), null));
    }

    @Test
    void rejectsNonParticipant() {
        when(chatRoomRepository.findById(5L)).thenReturn(Optional.of(ChatRoom.createPersonalRoom()));

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/user/queue/chat-rooms/5", () -> "12"), null));
    }

    @Test
    void rejectsUnauthenticatedConnection() {
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                frame(StompCommand.CONNECT, null, null), null));
    }

    @Test
    void rejectsOtherDestinationsAndClientSend() {
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/other", () -> "12"), null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/chat-rooms/5", () -> "12"), null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                frame(StompCommand.SEND, "/user/queue/chat-rooms/5", () -> "12"), null));
    }

    private Message<byte[]> frame(StompCommand command, String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) accessor.setDestination(destination);
        if (user != null) accessor.setUser(user);
        return org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0],
                accessor.getMessageHeaders());
    }
}
