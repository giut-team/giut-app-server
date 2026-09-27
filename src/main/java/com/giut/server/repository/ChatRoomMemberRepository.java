package com.giut.server.repository;

import com.giut.server.entity.ChatRoomMember;
import com.giut.server.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    boolean existsByChatRoomIdAndUserIdAndLeftAtIsNull(Long chatRoomId, Long userId);

    List<ChatRoomMember> findAllByChatRoomIdAndLeftAtIsNull(Long chatRoomId);

    List<ChatRoomMember> findAllByUserIdAndLeftAtIsNull(Long userId);

    Optional<ChatRoomMember> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    @Query("""
            SELECT first.chatRoomId
            FROM ChatRoomMember first
            JOIN ChatRoomMember second ON first.chatRoomId = second.chatRoomId
            JOIN ChatRoom room ON room.id = first.chatRoomId
            WHERE first.userId = :userId
              AND second.userId = :targetUserId
              AND first.leftAt IS NULL
              AND second.leftAt IS NULL
              AND room.type = :type
              AND room.status = :status
            """)
    Optional<Long> findActivePersonalChatRoomId(
            @Param("userId") Long userId,
            @Param("targetUserId") Long targetUserId,
            @Param("type") ChatRoom.Type type,
            @Param("status") ChatRoom.Status status
    );
}
