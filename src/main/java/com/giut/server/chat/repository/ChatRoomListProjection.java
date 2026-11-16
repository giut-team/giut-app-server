package com.giut.server.chat.repository;

import com.giut.server.chat.entity.ChatRoom;

/**
 * 채팅방 목록에 필요한 값만 한 번에 조회하기 위한 projection.
 */
public interface ChatRoomListProjection {

    ChatRoom getChatRoom();

    Long getTargetUserId();
}
