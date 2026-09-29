package com.giut.server.repository;

import com.giut.server.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findAllByChatRoomIdAndStatusOrderByIdDesc(
            Long chatRoomId,
            ChatMessage.Status status,
            Pageable pageable
    );

    Page<ChatMessage> findAllByChatRoomIdAndStatusOrderByIdAsc(
            Long chatRoomId,
            ChatMessage.Status status,
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
