package com.giut.server.chat.repository;

import com.giut.server.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Optional<ChatMessage> findBySenderIdAndClientMessageId(
            Long senderId,
            String clientMessageId
    );

    Slice<ChatMessage> findByChatRoomIdAndStatusOrderByIdDesc(
            Long chatRoomId,
            ChatMessage.Status status,
            Pageable pageable
    );

    Slice<ChatMessage> findByChatRoomIdAndStatusAndIdLessThanOrderByIdDesc(
            Long chatRoomId,
            ChatMessage.Status status,
            Long beforeMessageId,
            Pageable pageable
    );

    long countByChatRoomIdAndStatusAndSenderIdNot(
            Long chatRoomId,
            ChatMessage.Status status,
            Long senderId
    );

    long countByChatRoomIdAndStatusAndIdGreaterThanAndSenderIdNot(
            Long chatRoomId,
            ChatMessage.Status status,
            Long messageId,
            Long senderId
    );
}
