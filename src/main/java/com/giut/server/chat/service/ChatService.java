package com.giut.server.chat.service;

import com.giut.server.chat.event.ChatMessageEvent;

import com.giut.server.chat.dto.request.CreatePersonalChatRoomRequest;
import com.giut.server.chat.dto.request.SendChatMessageRequest;
import com.giut.server.chat.dto.response.ChatMessageListResponse;
import com.giut.server.chat.dto.response.ChatMessageResponse;
import com.giut.server.chat.dto.response.ChatRoomResponse;
import com.giut.server.chat.dto.response.ChatRoomListResponse;
import com.giut.server.chat.dto.response.ChatRoomUnreadCountResponse;
import com.giut.server.chat.entity.ChatMessage;
import com.giut.server.chat.entity.ChatRoom;
import com.giut.server.chat.entity.ChatRoomMember;
import com.giut.server.user.entity.User;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.global.exception.ConflictException;
import com.giut.server.chat.repository.ChatMessageRepository;
import com.giut.server.chat.repository.ChatRoomMemberRepository;
import com.giut.server.chat.repository.ChatRoomRepository;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
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
    private final ApplicationEventPublisher eventPublisher;
    private final PersonalChatRoomCreator personalChatRoomCreator;
    private final ChatMessageCreator chatMessageCreator;

    public ChatRoomResponse createPersonalChatRoom(
            Long userId,
            CreatePersonalChatRoomRequest request
    ) {
        Long targetUserId = request.targetUserId();

        if (userId.equals(targetUserId)) {
            throw new IllegalArgumentException(
                    "자기 자신과는 개인 채팅방을 만들 수 없습니다."
            );
        }

        findActiveUser(
                userId,
                "사용자를 찾을 수 없습니다."
        );

        findActiveUser(
                targetUserId,
                "상대 사용자를 찾을 수 없습니다."
        );

        String key = ChatRoom.createPersonalChatKey(
                userId,
                targetUserId
        );

        return chatRoomRepository.findByPersonalChatKey(key)
                .map(room -> restoreAndRespond(
                        room,
                        userId,
                        targetUserId,
                        false
                ))
                .orElseGet(() -> createOrFindAfterConflict(
                        key,
                        userId,
                        targetUserId
                ));
    }

    private ChatRoomResponse createOrFindAfterConflict(
            String key,
            Long userId,
            Long targetUserId
    ) {
        try {
            ChatRoom room = personalChatRoomCreator.create(
                    userId,
                    targetUserId
            );

            return ChatRoomResponse.of(
                    room,
                    targetUserId,
                    true
            );
        } catch (DataIntegrityViolationException exception) {
            ChatRoom room = chatRoomRepository
                    .findByPersonalChatKey(key)
                    .orElseThrow(() -> exception);

            return restoreAndRespond(
                    room,
                    userId,
                    targetUserId,
                    false
            );
        }
    }

    public ChatMessageResponse sendMessage(Long userId, Long chatRoomId, SendChatMessageRequest request) {
        ChatRoom chatRoom = findActiveChatRoom(chatRoomId);

        ChatRoomMember participant =
                validateActiveParticipant(
                        chatRoom.getId(),
                        userId
                );

        return chatMessageRepository
                .findBySenderIdAndClientMessageId(
                        userId,
                        request.clientMessageId()
                )
                .map(message -> validateIdempotentRetry(
                        message,
                        chatRoom.getId(),
                        request.content()
                ))
                .orElseGet(() -> createOrFindMessageAfterConflict(
                        chatRoom.getId(),
                        userId,
                        participant.getId(),
                        request
                ));
    }

    private ChatMessageResponse createOrFindMessageAfterConflict(
            Long chatRoomId,
            Long userId,
            Long participantId,
            SendChatMessageRequest request
    ) {
        try {
            return chatMessageCreator.create(
                    chatRoomId,
                    userId,
                    participantId,
                    request.clientMessageId(),
                    request.content()
            );
        } catch (DataIntegrityViolationException exception) {
            ChatMessage existingMessage = chatMessageRepository
                    .findBySenderIdAndClientMessageId(
                            userId,
                            request.clientMessageId()
                    )
                    .orElseThrow(() -> exception);

            return validateIdempotentRetry(
                    existingMessage,
                    chatRoomId,
                    request.content()
            );
        }
    }

    private ChatMessageResponse validateIdempotentRetry(
            ChatMessage message,
            Long chatRoomId,
            String content
    ) {
        if (!Objects.equals(message.getChatRoomId(), chatRoomId)
                || !Objects.equals(message.getContent(), content)) {
            throw new ConflictException(
                    "동일한 clientMessageId를 다른 메시지에 재사용할 수 없습니다."
            );
        }

        return ChatMessageResponse.from(message);
    }

    @Transactional(readOnly = true)
    public ChatMessageListResponse getMessages(
            Long userId,
            Long chatRoomId,
            Long beforeMessageId,
            int size
    ) {
        ChatRoom chatRoom = findActiveChatRoom(chatRoomId);
        validateActiveParticipant(chatRoom.getId(), userId);

        if (beforeMessageId != null && beforeMessageId <= 0) {
            throw new IllegalArgumentException(
                    "메시지 커서는 1 이상이어야 합니다."
            );
        }

        int normalizedSize = normalizeSize(size);
        PageRequest pageable = PageRequest.of(0, normalizedSize);

        Slice<ChatMessage> messageSlice =
                beforeMessageId == null
                        ? chatMessageRepository
                        .findByChatRoomIdAndStatusOrderByIdDesc(
                                chatRoom.getId(),
                                ChatMessage.Status.ACTIVE,
                                pageable
                        )
                        : chatMessageRepository
                        .findByChatRoomIdAndStatusAndIdLessThanOrderByIdDesc(
                                chatRoom.getId(),
                                ChatMessage.Status.ACTIVE,
                                beforeMessageId,
                                pageable
                        );

        List<ChatMessage> descendingMessages =
                messageSlice.getContent();

        Long nextCursor =
                messageSlice.hasNext() && !descendingMessages.isEmpty()
                        ? descendingMessages
                        .get(descendingMessages.size() - 1)
                        .getId()
                        : null;

        List<ChatMessageResponse> messages =
                descendingMessages.stream()
                        .sorted(
                                java.util.Comparator.comparing(
                                        ChatMessage::getId
                                )
                        )
                        .map(ChatMessageResponse::from)
                        .toList();

        return new ChatMessageListResponse(
                chatRoom.getId(),
                nextCursor,
                normalizedSize,
                messageSlice.hasNext(),
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
    public void leaveChatRoom(Long userId, Long chatRoomId) {
        ChatRoom chatRoom = findActiveChatRoom(chatRoomId);
        ChatRoomMember participant = validateActiveParticipant(chatRoom.getId(), userId);
        participant.leave();
    }

    @Transactional
    public void markChatRoomAsRead(Long userId, Long chatRoomId, Long messageId) {

        ChatRoom chatRoom = findActiveChatRoom(chatRoomId);

        ChatRoomMember participant =
                validateActiveParticipant(
                        chatRoom.getId(),
                        userId
                );

        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("메시지를 찾을 수 없습니다."));

        if (!Objects.equals(message.getChatRoomId(), chatRoom.getId())) {
            throw new ResourceNotFoundException("해당 채팅방의 메시지가 아닙니다.");
        }

        chatRoomMemberRepository.updateLastReadMessageIdIfGreater(
                participant.getId(),
                messageId
        );
    }

    @Transactional(readOnly = true)
    public ChatRoomUnreadCountResponse getUnreadMessageCount(Long userId, Long chatRoomId) {
        ChatRoom chatRoom = findActiveChatRoom(chatRoomId);
        ChatRoomMember participant = validateActiveParticipant(chatRoom.getId(), userId);

        long unreadCount = participant.getLastReadMessageId() == null
                ? chatMessageRepository.countByChatRoomIdAndStatusAndSenderIdNot(
                        chatRoom.getId(), ChatMessage.Status.ACTIVE, userId)
                : chatMessageRepository.countByChatRoomIdAndStatusAndIdGreaterThanAndSenderIdNot(
                        chatRoom.getId(),
                        ChatMessage.Status.ACTIVE,
                        participant.getLastReadMessageId(),
                        userId
                );

        return new ChatRoomUnreadCountResponse(chatRoom.getId(), unreadCount);
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
            throw new ForbiddenException("본인이 보낸 메시지만 삭제할 수 있습니다.");
        }
        if (message.getStatus() == ChatMessage.Status.DELETED) {
            return;
        }

        message.delete();
        eventPublisher.publishEvent(new ChatMessageEvent(ChatMessageResponse.from(message)));
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
                .orElseThrow(() -> new ForbiddenException("채팅방 참여자만 메시지를 이용할 수 있습니다."));
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(size, MAX_PAGE_SIZE);
    }

    private User findActiveUser(Long userId, String notFoundMessage) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(notFoundMessage));
    }

    private ChatRoomResponse restoreAndRespond(
            ChatRoom chatRoom,
            Long userId,
            Long targetUserId,
            boolean created
    ) {
        personalChatRoomCreator.restoreParticipants(
                chatRoom.getId(),
                userId,
                targetUserId
        );

        return ChatRoomResponse.of(
                chatRoom,
                targetUserId,
                created
        );
    }

}
