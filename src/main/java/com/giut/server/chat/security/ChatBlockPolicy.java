package com.giut.server.chat.security;

import com.giut.server.chat.entity.ChatRoom;
import com.giut.server.chat.repository.ChatRoomMemberRepository;
import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.user.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatBlockPolicy {

    private static final String BLOCKED_INTERACTION_MESSAGE =
            "해당 사용자와 상호작용할 수 없습니다.";

    private final UserBlockRepository userBlockRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    public void requireUsersCanChat(Long userId, Long targetUserId) {
        if (isBlockedBetween(userId, targetUserId)) {
            throw new ForbiddenException(BLOCKED_INTERACTION_MESSAGE);
        }
    }

    public void requireRoomCanBeUsed(ChatRoom chatRoom, Long userId) {
        if (isRoomBlocked(chatRoom, userId)) {
            throw new ForbiddenException(BLOCKED_INTERACTION_MESSAGE);
        }
    }

    public boolean isRoomBlocked(ChatRoom chatRoom, Long userId) {
        if (chatRoom.getType() != ChatRoom.Type.PERSONAL) {
            return false;
        }

        return chatRoomMemberRepository
                .findFirstByChatRoomIdAndUserIdNot(chatRoom.getId(), userId)
                .map(member -> isBlockedBetween(userId, member.getUserId()))
                .orElse(false);
    }

    public boolean isBlockedBetween(Long firstUserId, Long secondUserId) {
        return firstUserId != null
                && secondUserId != null
                && !firstUserId.equals(secondUserId)
                && userBlockRepository.existsBetween(firstUserId, secondUserId);
    }
}
