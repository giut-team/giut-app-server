package com.giut.server.service;

import com.giut.server.dto.chat.request.CreatePersonalChatRoomRequest;
import com.giut.server.dto.chat.request.SendChatMessageRequest;
import com.giut.server.dto.chat.response.ChatMessageListResponse;
import com.giut.server.dto.chat.response.ChatMessageResponse;
import com.giut.server.dto.chat.response.ChatRoomResponse;
import com.giut.server.dto.chat.response.ChatRoomListResponse;
import com.giut.server.entity.ChatMessage;
import com.giut.server.entity.ChatRoom;
import com.giut.server.entity.ChatRoomMember;
import com.giut.server.entity.User;
import com.giut.server.exception.ResourceNotFoundException;
import com.giut.server.repository.ChatMessageRepository;
import com.giut.server.repository.ChatRoomMemberRepository;
import com.giut.server.repository.ChatRoomRepository;
import com.giut.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    @Transactional
    public ChatRoomResponse createPersonalChatRoom(Long userId, CreatePersonalChatRoomRequest request) {
        Long targetUserId = request.targetUserId();
        if (userId.equals(targetUserId)) {
            throw new IllegalArgumentException("자기 자신과는 개인 채팅방을 만들 수 없습니다.");
        }

        findActiveUser(userId, "사용자를 찾을 수 없습니다.");
        findActiveUser(targetUserId, "상대 사용자를 찾을 수 없습니다.");

        return chatRoomMemberRepository.findActivePersonalChatRoomId(
                        userId,
                        targetUserId,
                        ChatRoom.Type.PERSONAL,
                        ChatRoom.Status.ACTIVE
                )
                .flatMap(chatRoomRepository::findById)
                .map(chatRoom -> ChatRoomResponse.of(chatRoom, targetUserId, false))
                .orElseGet(() -> createNewPersonalChatRoom(userId, targetUserId));
    }

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

    @Transactional(readOnly = true)
    public ChatRoomListResponse getChatRooms(Long userId) {
        List<ChatRoomResponse> chatRooms = chatRoomMemberRepository.findAllByUserIdAndLeftAtIsNull(userId).stream()
                .map(ChatRoomMember::getChatRoomId)
                .distinct()
                .map(chatRoomRepository::findById)
                .flatMap(java.util.Optional::stream)
                .filter(chatRoom -> chatRoom.getType() == ChatRoom.Type.PERSONAL)
                .filter(chatRoom -> chatRoom.getStatus() == ChatRoom.Status.ACTIVE)
                .map(chatRoom -> ChatRoomResponse.of(chatRoom, findOtherParticipantId(chatRoom.getId(), userId), false))
                .toList();

        return new ChatRoomListResponse(chatRooms);
    }

    @Transactional(readOnly = true)
    public ChatRoomResponse getChatRoom(Long userId, Long chatRoomId) {
        ChatRoom chatRoom = findActiveChatRoom(chatRoomId);
        validateActiveParticipant(chatRoom.getId(), userId);

        return ChatRoomResponse.of(chatRoom, findOtherParticipantId(chatRoom.getId(), userId), false);
    }

    @Transactional
    public void deleteMessage(Long userId, Long chatRoomId, Long messageId) {
        ChatRoom chatRoom = findActiveChatRoom(chatRoomId);
        validateActiveParticipant(chatRoom.getId(), userId);

        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("메시지를 찾을 수 없습니다."));

        if (!Objects.equals(message.getChatRoomId(), chatRoom.getId())) {
            throw new ResourceNotFoundException("해당 채팅방의 메시지가 아닙니다.");
        }
        if (!Objects.equals(message.getSenderId(), userId)) {
            throw new IllegalArgumentException("본인이 보낸 메시지만 삭제할 수 있습니다.");
        }
        if (message.getStatus() == ChatMessage.Status.DELETED) {
            return;
        }

        message.delete();
    }

    private Long findOtherParticipantId(Long chatRoomId, Long userId) {
        return chatRoomMemberRepository.findAllByChatRoomIdAndLeftAtIsNull(chatRoomId).stream()
                .map(ChatRoomMember::getUserId)
                .filter(participantId -> !Objects.equals(participantId, userId))
                .findFirst()
                .orElse(null);
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

    private ChatRoomResponse createNewPersonalChatRoom(Long userId, Long targetUserId) {
        ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.createPersonalRoom());
        chatRoomMemberRepository.save(ChatRoomMember.join(chatRoom.getId(), userId));
        chatRoomMemberRepository.save(ChatRoomMember.join(chatRoom.getId(), targetUserId));
        return ChatRoomResponse.of(chatRoom, targetUserId, true);
    }

    private User findActiveUser(Long userId, String notFoundMessage) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(notFoundMessage));
    }
}
