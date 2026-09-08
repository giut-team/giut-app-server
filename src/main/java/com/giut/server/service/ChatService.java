package com.giut.server.service;

import com.giut.server.dto.chat.request.SendChatMessageRequest;
import com.giut.server.dto.chat.response.ChatMessageListResponse;
import com.giut.server.dto.chat.response.ChatMessageResponse;
import com.giut.server.entity.ChatMessage;
import com.giut.server.entity.ChatRoom;
import com.giut.server.entity.ChatRoomMember;
import com.giut.server.exception.ResourceNotFoundException;
import com.giut.server.repository.ChatMessageRepository;
import com.giut.server.repository.ChatRoomMemberRepository;
import com.giut.server.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatMessageResponse sendMessage(Long userId, Long chatRoomId, SendChatMessageRequest request) {
        ChatRoom chatRoom = findActiveChatRoom(chatRoomId);
        validateActiveParticipant(chatRoom.getId(), userId);

        ChatMessage message = chatMessageRepository.save(
                ChatMessage.createText(chatRoom.getId(), userId, request.content())
        );

        chatRoomMemberRepository.findByChatRoomIdAndUserId(chatRoom.getId(), userId)
                .ifPresent(member -> member.updateLastReadMessage(message.getId()));

        return ChatMessageResponse.from(message);
    }

    @Transactional
    public ChatMessageListResponse getMessages(Long userId, Long chatRoomId, int page, int size) {
        ChatRoom chatRoom = findActiveChatRoom(chatRoomId);
        ChatRoomMember participant = validateActiveParticipant(chatRoom.getId(), userId);

        int normalizedPage = Math.max(page, 0);
        int normalizedSize = normalizeSize(size);
        Page<ChatMessage> messagePage = chatMessageRepository.findAllByChatRoomIdAndStatusOrderByIdAsc(
                chatRoom.getId(),
                ChatMessage.Status.ACTIVE,
                PageRequest.of(normalizedPage, normalizedSize)
        );

        List<ChatMessageResponse> messages = messagePage.getContent().stream()
                .map(ChatMessageResponse::from)
                .toList();

        if (!messages.isEmpty()) {
            participant.updateLastReadMessage(messages.get(messages.size() - 1).messageId());
        }

        return new ChatMessageListResponse(
                chatRoom.getId(),
                normalizedPage,
                normalizedSize,
                messagePage.hasNext(),
                messages
        );
    }

    private ChatRoom findActiveChatRoom(Long chatRoomId) {
        return chatRoomRepository.findById(chatRoomId)
                .filter(chatRoom -> chatRoom.getStatus() == ChatRoom.Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("채팅방을 찾을 수 없습니다."));
    }

    private ChatRoomMember validateActiveParticipant(Long chatRoomId, Long userId) {
        return chatRoomMemberRepository.findByChatRoomIdAndUserId(chatRoomId, userId)
                .filter(member -> member.getLeftAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 참여자만 메시지를 이용할 수 있습니다."));
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(size, MAX_PAGE_SIZE);
    }
}
