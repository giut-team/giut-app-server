package com.giut.server.chat.security;

import com.giut.server.chat.entity.ChatRoom;
import com.giut.server.chat.repository.ChatRoomMemberRepository;
import com.giut.server.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ChatSubscriptionInterceptor implements ChannelInterceptor {

    private static final Pattern ROOM_QUEUE =
            Pattern.compile("^/user/queue/chat-rooms/([1-9][0-9]*)$");

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.SEND) {
            throw new AccessDeniedException("메시지는 REST API로 전송하세요.");
        }

        if (accessor.getCommand() != StompCommand.CONNECT
                && accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        Principal user = accessor.getUser();
        if (user == null) {
            throw new AccessDeniedException("인증이 필요합니다.");
        }
        if (accessor.getCommand() == StompCommand.CONNECT) {
            return message;
        }

        Matcher matcher = ROOM_QUEUE.matcher(
                String.valueOf(accessor.getDestination()));
        if (!matcher.matches()) {
            throw new AccessDeniedException("허용되지 않은 구독 경로입니다.");
        }

        long roomId;
        long userId;
        try {
            roomId = Long.parseLong(matcher.group(1));
            userId = Long.parseLong(user.getName());
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("잘못된 구독 요청입니다.");
        }

        boolean activeRoom = chatRoomRepository.findById(roomId)
                .filter(room -> room.getStatus() == ChatRoom.Status.ACTIVE)
                .isPresent();
        boolean participant = chatRoomMemberRepository
                .existsByChatRoomIdAndUserIdAndLeftAtIsNull(roomId, userId);

        if (!activeRoom || !participant) {
            throw new AccessDeniedException("채팅방 참여자만 구독할 수 있습니다.");
        }
        return message;
    }
}
