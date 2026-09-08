package com.giut.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "chat_messages",
        indexes = {
                @Index(name = "idx_chat_messages_room_id_id", columnList = "chat_room_id, id"),
                @Index(name = "idx_chat_messages_sender_id", columnList = "sender_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private MessageType messageType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    public enum MessageType {
        TEXT,
        IMAGE,
        SYSTEM
    }

    public enum Status {
        ACTIVE,
        DELETED
    }

    public static ChatMessage createText(Long chatRoomId, Long senderId, String content) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.chatRoomId = chatRoomId;
        chatMessage.senderId = senderId;
        chatMessage.content = content;
        chatMessage.messageType = MessageType.TEXT;
        chatMessage.status = Status.ACTIVE;
        return chatMessage;
    }

    public void delete() {
        this.status = Status.DELETED;
    }
}
