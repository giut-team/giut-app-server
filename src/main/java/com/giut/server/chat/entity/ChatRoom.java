package com.giut.server.chat.entity;

import com.giut.server.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "chat_rooms",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_rooms_team_id",
                        columnNames = "team_id"
                ),
                @UniqueConstraint(
                        name = "uk_chat_rooms_personal_chat_key",
                        columnNames = "personal_chat_key"
                )
        },
        indexes = {
                @Index(
                        name = "idx_chat_rooms_team_id",
                        columnList = "team_id"
                ),
                @Index(
                        name = "idx_chat_rooms_type_status",
                        columnList = "type, status"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "personal_chat_key", length = 100)
    private String personalChatKey;

    @Column(name = "team_id")
    private Long teamId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    public enum Type {
        PERSONAL,
        TEAM
    }

    public enum Status {
        ACTIVE,
        CLOSED,
        DELETED
    }

    public static ChatRoom createTeamRoom(Long teamId) {
        ChatRoom chatRoom = new ChatRoom();
        chatRoom.teamId = teamId;
        chatRoom.type = Type.TEAM;
        chatRoom.status = Status.ACTIVE;
        return chatRoom;
    }

    public static String createPersonalChatKey(
            Long firstUserId,
            Long secondUserId
    ) {
        if (firstUserId.equals(secondUserId)) {
            throw new IllegalArgumentException(
                    "자기 자신과는 개인 채팅방을 만들 수 없습니다."
            );
        }

        long smallerId = Math.min(firstUserId, secondUserId);
        long largerId = Math.max(firstUserId, secondUserId);

        return smallerId + ":" + largerId;
    }

    public static ChatRoom createPersonalRoom(
            Long firstUserId,
            Long secondUserId
    ) {
        ChatRoom chatRoom = new ChatRoom();
        chatRoom.type = Type.PERSONAL;
        chatRoom.status = Status.ACTIVE;
        chatRoom.personalChatKey = createPersonalChatKey(firstUserId, secondUserId);

        return chatRoom;
    }

    public void close() {
        this.status = Status.CLOSED;
    }

    public void delete() {
        this.status = Status.DELETED;
    }
}
