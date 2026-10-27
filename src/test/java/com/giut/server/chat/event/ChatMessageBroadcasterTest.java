package com.giut.server.chat.event;

import com.giut.server.chat.dto.response.ChatMessageResponse;
import com.giut.server.chat.entity.ChatMessage;
import com.giut.server.chat.entity.ChatRoomMember;
import com.giut.server.chat.repository.ChatRoomMemberRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

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
        UserRepository users = mock(UserRepository.class);
        ChatMessageResponse response = new ChatMessageResponse(30L, 5L, 12L, "hello",
                ChatMessage.MessageType.TEXT, ChatMessage.Status.ACTIVE, null);
        when(members.findAllByChatRoomIdAndLeftAtIsNull(5L))
                .thenReturn(List.of(ChatRoomMember.join(5L, 12L), ChatRoomMember.join(5L, 13L)));
        when(users.findAllByIdInAndStatus(List.of(12L, 13L), User.Status.ACTIVE))
                .thenReturn(List.of(user(12L), user(13L)));

        new ChatMessageBroadcaster(messagingTemplate, members, users).broadcast(new ChatMessageEvent(response));

        verify(messagingTemplate).convertAndSendToUser("12", "/queue/chat-rooms/5", response);
        verify(messagingTemplate).convertAndSendToUser("13", "/queue/chat-rooms/5", response);
    }

    @Test
    void doesNotBroadcastToFormerParticipant() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ChatRoomMemberRepository members = mock(ChatRoomMemberRepository.class);
        UserRepository users = mock(UserRepository.class);
        ChatMessageResponse response = new ChatMessageResponse(30L, 5L, 12L, "hello",
                ChatMessage.MessageType.TEXT, ChatMessage.Status.ACTIVE, null);
        when(members.findAllByChatRoomIdAndLeftAtIsNull(5L))
                .thenReturn(List.of(ChatRoomMember.join(5L, 12L)));
        when(users.findAllByIdInAndStatus(List.of(12L), User.Status.ACTIVE)).thenReturn(List.of(user(12L)));

        new ChatMessageBroadcaster(messagingTemplate, members, users).broadcast(new ChatMessageEvent(response));

        verify(messagingTemplate).convertAndSendToUser("12", "/queue/chat-rooms/5", response);
        verify(messagingTemplate, never()).convertAndSendToUser("13", "/queue/chat-rooms/5", response);
    }

    @Test
    void withdrawnAccountIsExcludedEvenWithAnExistingSubscriptionAndMembership() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        ChatRoomMemberRepository members = mock(ChatRoomMemberRepository.class);
        UserRepository users = mock(UserRepository.class);
        ChatMessageResponse response = new ChatMessageResponse(30L, 5L, 12L, "hello",
                ChatMessage.MessageType.TEXT, ChatMessage.Status.ACTIVE, null);
        when(members.findAllByChatRoomIdAndLeftAtIsNull(5L))
                .thenReturn(List.of(ChatRoomMember.join(5L, 12L), ChatRoomMember.join(5L, 13L)));
        when(users.findAllByIdInAndStatus(List.of(12L, 13L), User.Status.ACTIVE))
                .thenReturn(List.of(user(12L)));

        new ChatMessageBroadcaster(messaging, members, users).broadcast(new ChatMessageEvent(response));

        verify(messaging).convertAndSendToUser("12", "/queue/chat-rooms/5", response);
        verify(messaging, never()).convertAndSendToUser("13", "/queue/chat-rooms/5", response);
    }

    @Test
    void emptyRoomDoesNotQueryAccountStatus() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        ChatRoomMemberRepository members = mock(ChatRoomMemberRepository.class);
        UserRepository users = mock(UserRepository.class);
        ChatMessageResponse response = new ChatMessageResponse(30L, 5L, 12L, "hello",
                ChatMessage.MessageType.TEXT, ChatMessage.Status.ACTIVE, null);

        new ChatMessageBroadcaster(messaging, members, users).broadcast(new ChatMessageEvent(response));

        org.mockito.Mockito.verifyNoInteractions(messaging, users);
    }

    private User user(Long id) {
        User user = User.createOAuthUser("chat-" + id + "@example.com", "참여자", User.OAuthProvider.KAKAO, id.toString());
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
