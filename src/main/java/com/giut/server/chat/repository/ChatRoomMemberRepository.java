package com.giut.server.chat.repository;

import com.giut.server.chat.entity.ChatRoomMember;
import com.giut.server.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    boolean existsByChatRoomIdAndUserIdAndLeftAtIsNull(Long chatRoomId, Long userId);

    List<ChatRoomMember> findAllByChatRoomIdAndLeftAtIsNull(Long chatRoomId);

    List<ChatRoomMember> findAllByUserIdAndLeftAtIsNull(Long userId);

    @Query("""
            SELECT room AS chatRoom,
                   other.userId AS targetUserId
            FROM ChatRoomMember mine
            JOIN ChatRoom room
              ON room.id = mine.chatRoomId
            JOIN ChatRoomMember other
              ON other.chatRoomId = room.id
             AND other.userId <> :userId
            WHERE mine.userId = :userId
              AND mine.leftAt IS NULL
              AND room.type = :type
              AND room.status = :status
              AND NOT EXISTS (
                  SELECT blockedRelation.id
                  FROM UserBlock blockedRelation
                  WHERE (
                      blockedRelation.blocker.id = :userId
                      AND blockedRelation.blockedUser.id = other.userId
                  ) OR (
                      blockedRelation.blocker.id = other.userId
                      AND blockedRelation.blockedUser.id = :userId
                  )
              )
            ORDER BY mine.id ASC
            """)
    List<ChatRoomListProjection> findAvailablePersonalChatRooms(
            @Param("userId") Long userId,
            @Param("type") ChatRoom.Type type,
            @Param("status") ChatRoom.Status status
    );

    Optional<ChatRoomMember> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    Optional<ChatRoomMember> findFirstByChatRoomIdAndUserIdNot(
            Long chatRoomId,
            Long userId
    );

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

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
        UPDATE ChatRoomMember member
        SET member.lastReadMessageId = :messageId
        WHERE member.id = :memberId
          AND (
              member.lastReadMessageId IS NULL
              OR member.lastReadMessageId < :messageId
          )
        """)
    int updateLastReadMessageIdIfGreater(
            @Param("memberId") Long memberId,
            @Param("messageId") Long messageId
    );
}
