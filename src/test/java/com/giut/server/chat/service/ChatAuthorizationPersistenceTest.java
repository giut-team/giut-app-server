package com.giut.server.chat.service;

import com.giut.server.chat.dto.request.SendChatMessageRequest;
import com.giut.server.chat.entity.ChatMessage;
import com.giut.server.chat.entity.ChatRoom;
import com.giut.server.chat.entity.ChatRoomMember;
import com.giut.server.chat.repository.ChatMessageRepository;
import com.giut.server.chat.repository.ChatRoomMemberRepository;
import com.giut.server.chat.repository.ChatRoomRepository;
import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat_authorization_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class ChatAuthorizationPersistenceTest {

    @Autowired ChatService chatService;
    @Autowired UserRepository userRepository;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired ChatRoomMemberRepository chatRoomMemberRepository;
    @Autowired ChatMessageRepository chatMessageRepository;

    private User firstUser;
    private User secondUser;
    private User outsider;
    private ChatRoom chatRoom;
    private ChatMessage secondUserMessage;

    @BeforeEach
    void setUp() {
        firstUser = saveUser("auth-first@example.com", "auth-first-id");
        secondUser = saveUser("auth-second@example.com", "auth-second-id");
        outsider = saveUser("auth-outsider@example.com", "auth-outsider-id");

        chatRoom = chatRoomRepository.saveAndFlush(
                ChatRoom.createPersonalRoom(firstUser.getId(), secondUser.getId())
        );
        chatRoomMemberRepository.saveAndFlush(
                ChatRoomMember.join(chatRoom.getId(), firstUser.getId())
        );
        chatRoomMemberRepository.saveAndFlush(
                ChatRoomMember.join(chatRoom.getId(), secondUser.getId())
        );
        secondUserMessage = chatMessageRepository.saveAndFlush(
                ChatMessage.createText(chatRoom.getId(), secondUser.getId(), "상대방 메시지")
        );
    }

    @AfterEach
    void cleanUp() {
        chatMessageRepository.deleteAll();
        chatRoomMemberRepository.deleteAll();
        chatRoomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void nonParticipantCannotUseAnyRoomScopedRestOperation() {
        assertForbidden(() -> chatService.getChatRoom(outsider.getId(), chatRoom.getId()));
        assertForbidden(() -> chatService.getMessages(outsider.getId(), chatRoom.getId(), null, 30));
        assertForbidden(() -> chatService.getUnreadMessageCount(outsider.getId(), chatRoom.getId()));
        assertForbidden(() -> chatService.markChatRoomAsRead(
                outsider.getId(), chatRoom.getId(), secondUserMessage.getId()));
        assertForbidden(() -> chatService.sendMessage(
                outsider.getId(),
                chatRoom.getId(),
                new SendChatMessageRequest(UUID.randomUUID().toString(), "침입 메시지")
        ));
        assertForbidden(() -> chatService.deleteMessage(
                outsider.getId(), chatRoom.getId(), secondUserMessage.getId()));
        assertForbidden(() -> chatService.leaveChatRoom(outsider.getId(), chatRoom.getId()));
    }

    @Test
    void formerParticipantCannotUseRoomAfterLeaving() {
        chatService.leaveChatRoom(firstUser.getId(), chatRoom.getId());

        assertForbidden(() -> chatService.getChatRoom(firstUser.getId(), chatRoom.getId()));
        assertForbidden(() -> chatService.getMessages(firstUser.getId(), chatRoom.getId(), null, 30));
        assertForbidden(() -> chatService.sendMessage(
                firstUser.getId(),
                chatRoom.getId(),
                new SendChatMessageRequest(UUID.randomUUID().toString(), "퇴장 후 메시지")
        ));
    }

    @Test
    void participantCannotDeleteAnotherParticipantsMessage() {
        assertThatThrownBy(() -> chatService.deleteMessage(
                firstUser.getId(),
                chatRoom.getId(),
                secondUserMessage.getId()
        ))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("본인이 보낸 메시지만 삭제할 수 있습니다.");
    }

    @Test
    void messageFromAnotherRoomCannotBeDeletedThroughCurrentRoom() {
        ChatRoom anotherRoom = chatRoomRepository.saveAndFlush(
                ChatRoom.createPersonalRoom(firstUser.getId(), outsider.getId())
        );
        chatRoomMemberRepository.saveAndFlush(
                ChatRoomMember.join(anotherRoom.getId(), firstUser.getId())
        );
        chatRoomMemberRepository.saveAndFlush(
                ChatRoomMember.join(anotherRoom.getId(), outsider.getId())
        );
        ChatMessage anotherRoomMessage = chatMessageRepository.saveAndFlush(
                ChatMessage.createText(anotherRoom.getId(), firstUser.getId(), "다른 방 메시지")
        );

        assertThatThrownBy(() -> chatService.deleteMessage(
                firstUser.getId(),
                chatRoom.getId(),
                anotherRoomMessage.getId()
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("해당 채팅방의 메시지가 아닙니다.");
    }

    private User saveUser(String email, String oauthId) {
        return userRepository.saveAndFlush(
                User.createOAuthUser(email, "채팅 사용자", User.OAuthProvider.KAKAO, oauthId)
        );
    }

    private void assertForbidden(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("채팅방 참여자만 메시지를 이용할 수 있습니다.");
    }
}
