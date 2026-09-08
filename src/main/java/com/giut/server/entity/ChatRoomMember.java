package com.giut.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "chat_room_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_room_members_room_user",
                columnNames = {"chat_room_id", "user_id"}
        ),
        indexes = {
                @Index(name = "idx_chat_room_members_room_id", columnList = "chat_room_id"),
                @Index(name = "idx_chat_room_members_user_id", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    public static ChatRoomMember join(Long chatRoomId, Long userId) {
        ChatRoomMember chatRoomMember = new ChatRoomMember();
        chatRoomMember.chatRoomId = chatRoomId;
        chatRoomMember.userId = userId;
        chatRoomMember.joinedAt = Instant.now();
        return chatRoomMember;
    }

    public void updateLastReadMessage(Long messageId) {
        this.lastReadMessageId = messageId;
    }

    public void leave() {
        this.leftAt = Instant.now();
    }
}
