package com.giut.server.repository;

import com.giut.server.entity.ChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    boolean existsByChatRoomIdAndUserIdAndLeftAtIsNull(Long chatRoomId, Long userId);

    List<ChatRoomMember> findAllByChatRoomIdAndLeftAtIsNull(Long chatRoomId);

    List<ChatRoomMember> findAllByUserIdAndLeftAtIsNull(Long userId);

    Optional<ChatRoomMember> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);
}
