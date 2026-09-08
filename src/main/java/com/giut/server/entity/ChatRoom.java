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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "chat_rooms",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_rooms_team_id",
                columnNames = "team_id"
        ),
        indexes = {
                @Index(name = "idx_chat_rooms_team_id", columnList = "team_id"),
                @Index(name = "idx_chat_rooms_type_status", columnList = "type, status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    public static ChatRoom createPersonalRoom() {
        ChatRoom chatRoom = new ChatRoom();
        chatRoom.type = Type.PERSONAL;
        chatRoom.status = Status.ACTIVE;
        return chatRoom;
    }

    public void close() {
        this.status = Status.CLOSED;
    }

    public void delete() {
        this.status = Status.DELETED;
    }
}
