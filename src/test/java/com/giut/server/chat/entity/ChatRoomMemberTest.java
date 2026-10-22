package com.giut.server.chat.entity;

import com.giut.server.chat.entity.ChatRoomMember;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRoomMemberTest {

    @Test
    void lastReadMessageOnlyMovesForward() {
        ChatRoomMember member = ChatRoomMember.join(1L, 2L);

        member.updateLastReadMessage(100L);
        member.updateLastReadMessage(30L);
        assertThat(member.getLastReadMessageId()).isEqualTo(100L);

        member.updateLastReadMessage(101L);
        assertThat(member.getLastReadMessageId()).isEqualTo(101L);
    }
}
