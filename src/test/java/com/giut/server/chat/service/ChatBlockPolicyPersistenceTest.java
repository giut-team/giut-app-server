package com.giut.server.chat.service;

import com.giut.server.chat.dto.request.CreatePersonalChatRoomRequest;
import com.giut.server.chat.dto.request.SendChatMessageRequest;
import com.giut.server.chat.dto.response.ChatRoomResponse;
import com.giut.server.chat.entity.ChatMessage;
import com.giut.server.chat.repository.ChatMessageRepository;
import com.giut.server.chat.repository.ChatRoomMemberRepository;
import com.giut.server.chat.repository.ChatRoomRepository;
import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.user.entity.User;
import com.giut.server.user.entity.UserBlock;
import com.giut.server.user.repository.UserBlockRepository;
import com.giut.server.user.repository.UserRepository;
import com.giut.server.user.service.UserBlockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat_block_policy_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=",
        "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF"
})
class ChatBlockPolicyPersistenceTest {

    @Autowired ChatService chatService;
    @Autowired UserRepository userRepository;
    @Autowired UserBlockRepository userBlockRepository;
    @Autowired UserBlockService userBlockService;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired ChatRoomMemberRepository chatRoomMemberRepository;
    @Autowired ChatMessageRepository chatMessageRepository;

    private User firstUser;
    private User secondUser;
    private Long chatRoomId;
    private ChatMessage existingMessage;

    @BeforeEach
    void setUp() {
        firstUser = saveUser("chat-block-first@example.com", "chat-block-first-id");
        secondUser = saveUser("chat-block-second@example.com", "chat-block-second-id");

        ChatRoomResponse room = chatService.createPersonalChatRoom(
                firstUser.getId(),
                new CreatePersonalChatRoomRequest(secondUser.getId())
        );
        chatRoomId = room.chatRoomId();
        existingMessage = chatMessageRepository.saveAndFlush(
                ChatMessage.createText(chatRoomId, firstUser.getId(), "차단 전 메시지")
        );
    }

    @AfterEach
    void cleanUp() {
        userBlockRepository.deleteAll();
        chatMessageRepository.deleteAll();
        chatRoomMemberRepository.deleteAll();
        chatRoomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void oneDirectionalBlockHidesRoomAndBlocksBothUsersFromAllChatAccess() {
        block(firstUser, secondUser);

        assertThat(chatService.getChatRooms(firstUser.getId()).chatRooms()).isEmpty();
        assertThat(chatService.getChatRooms(secondUser.getId()).chatRooms()).isEmpty();

        assertBlocked(() -> chatService.getChatRoom(firstUser.getId(), chatRoomId));
        assertBlocked(() -> chatService.getMessages(secondUser.getId(), chatRoomId, null, null, 30));
        assertBlocked(() -> chatService.getMessages(secondUser.getId(), chatRoomId, null, 0L, 30));
        assertBlocked(() -> chatService.getUnreadMessageCount(firstUser.getId(), chatRoomId));
        assertBlocked(() -> chatService.markChatRoomAsRead(
                secondUser.getId(), chatRoomId, existingMessage.getId()));
        assertBlocked(() -> chatService.sendMessage(
                firstUser.getId(),
                chatRoomId,
                new SendChatMessageRequest(UUID.randomUUID().toString(), "차단 후 메시지")
        ));
        assertBlocked(() -> chatService.deleteMessage(
                firstUser.getId(), chatRoomId, existingMessage.getId()));
    }

    @Test
    void blockedUsersCannotCreateOrRestorePersonalRoomInEitherDirection() {
        block(secondUser, firstUser);

        assertBlocked(() -> chatService.createPersonalChatRoom(
                firstUser.getId(), new CreatePersonalChatRoomRequest(secondUser.getId())));
        assertBlocked(() -> chatService.createPersonalChatRoom(
                secondUser.getId(), new CreatePersonalChatRoomRequest(firstUser.getId())));

        assertThat(chatRoomRepository.count()).isEqualTo(1);
        assertThat(chatMessageRepository.count()).isEqualTo(1);
    }

    @Test
    void unblockingRestoresExistingRoomAndMessagesWithoutCreatingAnotherRoom() {
        block(firstUser, secondUser);
        userBlockService.unblock(firstUser.getId(), secondUser.getId());

        assertThat(chatService.getChatRooms(firstUser.getId()).chatRooms()).hasSize(1);
        assertThat(chatService.getMessages(firstUser.getId(), chatRoomId, null, null, 30).messages())
                .extracting(message -> message.messageId())
                .containsExactly(existingMessage.getId());

        ChatRoomResponse reused = chatService.createPersonalChatRoom(
                firstUser.getId(), new CreatePersonalChatRoomRequest(secondUser.getId()));
        assertThat(reused.chatRoomId()).isEqualTo(chatRoomId);
        assertThat(reused.created()).isFalse();
        assertThat(chatRoomRepository.count()).isEqualTo(1);
    }

    @Test
    void leavingRoomRemainsAvailableWhileBlocked() {
        block(firstUser, secondUser);

        chatService.leaveChatRoom(firstUser.getId(), chatRoomId);

        assertThat(chatRoomMemberRepository
                .findByChatRoomIdAndUserId(chatRoomId, firstUser.getId())
                .orElseThrow().getLeftAt()).isNotNull();
    }

    private User saveUser(String email, String oauthId) {
        return userRepository.saveAndFlush(
                User.createOAuthUser(email, "차단 테스트 사용자", User.OAuthProvider.KAKAO, oauthId)
        );
    }

    private void block(User blocker, User blockedUser) {
        userBlockRepository.saveAndFlush(UserBlock.create(blocker, blockedUser));
    }

    private void assertBlocked(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("해당 사용자와 상호작용할 수 없습니다.");
    }
}
