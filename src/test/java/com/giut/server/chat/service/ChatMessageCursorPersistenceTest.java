package com.giut.server.chat.service;

import com.giut.server.chat.dto.response.ChatMessageListResponse;
import com.giut.server.chat.entity.ChatMessage;
import com.giut.server.chat.entity.ChatRoom;
import com.giut.server.chat.entity.ChatRoomMember;
import com.giut.server.chat.repository.ChatMessageRepository;
import com.giut.server.chat.repository.ChatRoomMemberRepository;
import com.giut.server.chat.repository.ChatRoomRepository;
import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat_cursor_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class ChatMessageCursorPersistenceTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private User firstUser;
    private User secondUser;
    private User outsider;
    private ChatRoom chatRoom;
    private ChatRoomMember firstParticipant;
    private List<ChatMessage> savedMessages;

    @BeforeEach
    void setUp() {
        firstUser = userRepository.saveAndFlush(
                User.createOAuthUser(
                        "cursor-first@example.com",
                        "첫 번째 사용자",
                        User.OAuthProvider.KAKAO,
                        "cursor-first-oauth-id"
                )
        );

        secondUser = userRepository.saveAndFlush(
                User.createOAuthUser(
                        "cursor-second@example.com",
                        "두 번째 사용자",
                        User.OAuthProvider.KAKAO,
                        "cursor-second-oauth-id"
                )
        );

        outsider = userRepository.saveAndFlush(
                User.createOAuthUser(
                        "cursor-outsider@example.com",
                        "외부 사용자",
                        User.OAuthProvider.KAKAO,
                        "cursor-outsider-oauth-id"
                )
        );

        chatRoom = chatRoomRepository.saveAndFlush(
                ChatRoom.createPersonalRoom(
                        firstUser.getId(),
                        secondUser.getId()
                )
        );

        firstParticipant = chatRoomMemberRepository.saveAndFlush(
                ChatRoomMember.join(
                        chatRoom.getId(),
                        firstUser.getId()
                )
        );

        chatRoomMemberRepository.saveAndFlush(
                ChatRoomMember.join(
                        chatRoom.getId(),
                        secondUser.getId()
                )
        );

        savedMessages = IntStream.rangeClosed(1, 100)
                .mapToObj(number -> ChatMessage.createText(
                        chatRoom.getId(),
                        number % 2 == 0
                                ? firstUser.getId()
                                : secondUser.getId(),
                        "메시지 " + number
                ))
                .toList();

        savedMessages = chatMessageRepository.saveAllAndFlush(savedMessages);
    }

    @AfterEach
    void cleanUp() {
        chatMessageRepository.deleteAll();
        chatRoomMemberRepository.deleteAll();
        chatRoomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void returnsLatestMessagesOnFirstRequest() {
        ChatMessageListResponse response = chatService.getMessages(
                firstUser.getId(),
                chatRoom.getId(),
                null,
                30
        );

        assertThat(response.messages()).hasSize(30);
        assertThat(response.hasNext()).isTrue();

        // 최신 30개를 화면 표시 순서인 오래된 순으로 반환
        assertThat(messageIds(response))
                .containsExactlyElementsOf(
                        savedMessages.subList(70, 100).stream()
                                .map(ChatMessage::getId)
                                .toList()
                );

        // 다음 요청에서는 71번째 메시지보다 과거 메시지를 조회
        assertThat(response.nextCursor())
                .isEqualTo(savedMessages.get(70).getId());
    }

    @Test
    void returnsMessagesOlderThanCursor() {
        Long cursor = savedMessages.get(70).getId();

        ChatMessageListResponse response = chatService.getMessages(
                firstUser.getId(),
                chatRoom.getId(),
                cursor,
                30
        );

        assertThat(response.messages()).hasSize(30);
        assertThat(response.hasNext()).isTrue();

        assertThat(messageIds(response))
                .containsExactlyElementsOf(
                        savedMessages.subList(40, 70).stream()
                                .map(ChatMessage::getId)
                                .toList()
                );

        assertThat(response.nextCursor())
                .isEqualTo(savedMessages.get(40).getId());
    }

    @Test
    void returnsNullCursorWhenNoOlderMessagesRemain() {
        Long cursor = savedMessages.get(10).getId();

        ChatMessageListResponse response = chatService.getMessages(
                firstUser.getId(),
                chatRoom.getId(),
                cursor,
                30
        );

        assertThat(response.messages()).hasSize(10);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();

        assertThat(messageIds(response))
                .containsExactlyElementsOf(
                        savedMessages.subList(0, 10).stream()
                                .map(ChatMessage::getId)
                                .toList()
                );
    }

    @Test
    void newMessageDoesNotChangeNextCursorResult() {
        ChatMessageListResponse firstPage = chatService.getMessages(
                firstUser.getId(),
                chatRoom.getId(),
                null,
                30
        );

        // 첫 페이지 조회 이후 새로운 메시지가 도착한 상황
        chatMessageRepository.saveAndFlush(
                ChatMessage.createText(
                        chatRoom.getId(),
                        secondUser.getId(),
                        "새로 도착한 메시지"
                )
        );

        ChatMessageListResponse secondPage = chatService.getMessages(
                firstUser.getId(),
                chatRoom.getId(),
                firstPage.nextCursor(),
                30
        );

        // 새 메시지가 추가돼도 두 번째 페이지는 기존 41~70번 범위를 반환
        assertThat(messageIds(secondPage))
                .containsExactlyElementsOf(
                        savedMessages.subList(40, 70).stream()
                                .map(ChatMessage::getId)
                                .toList()
                );
    }

    @Test
    void messageLookupDoesNotAutomaticallyMarkMessagesAsRead() {
        assertThat(firstParticipant.getLastReadMessageId()).isNull();

        chatService.getMessages(
                firstUser.getId(),
                chatRoom.getId(),
                null,
                30
        );

        ChatRoomMember participantAfterLookup =
                chatRoomMemberRepository.findById(
                        firstParticipant.getId()
                ).orElseThrow();

        assertThat(participantAfterLookup.getLastReadMessageId())
                .isNull();
    }

    @Test
    void nonParticipantCannotReadMessages() {
        assertThatThrownBy(() -> chatService.getMessages(
                outsider.getId(),
                chatRoom.getId(),
                null,
                30
        ))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("채팅방 참여자만 메시지를 이용할 수 있습니다.");
    }

    private List<Long> messageIds(ChatMessageListResponse response) {
        return response.messages().stream()
                .map(message -> message.messageId())
                .toList();
    }
}