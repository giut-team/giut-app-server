package com.giut.server.repository;

import com.giut.server.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByTeamIdAndType(Long teamId, ChatRoom.Type type);
}
