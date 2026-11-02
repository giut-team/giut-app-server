package com.giut.server.chat.service;

import com.giut.server.chat.dto.request.SendChatMessageRequest;
import com.giut.server.chat.dto.response.ChatMessageResponse;
import com.giut.server.chat.entity.ChatMessage;
import com.giut.server.chat.entity.ChatRoom;
import com.giut.server.chat.entity.ChatRoomMember;
import com.giut.server.chat.repository.ChatMessageRepository;
import com.giut.server.chat.repository.ChatRoomMemberRepository;
import com.giut.server.chat.repository.ChatRoomRepository;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat_read_state_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class ChatReadStatePersistenceTest {

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
    private ChatRoom chatRoom;
    private ChatRoomMember firstParticipant;
    private ChatRoomMember secondParticipant;

    @BeforeEach
    void setUp() {
        firstUser = userRepository.saveAndFlush(
                User.createOAuthUser(
                        "read-first@example.com",
                        "첫 번째 사용자",
                        User.OAuthProvider.KAKAO,
                        "read-first-oauth-id"
                )
        );

        secondUser = userRepository.saveAndFlush(
                User.createOAuthUser(
                        "read-second@example.com",
                        "두 번째 사용자",
                        User.OAuthProvider.KAKAO,
                        "read-second-oauth-id"
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

        secondParticipant = chatRoomMemberRepository.saveAndFlush(
                ChatRoomMember.join(
                        chatRoom.getId(),
                        secondUser.getId()
                )
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
    void unreadCountIncludesOnlyActiveMessagesSentByOtherUser() {
        saveMessage(firstUser.getId(), "내 메시지");
        saveMessage(secondUser.getId(), "상대방 메시지 1");
        saveMessage(secondUser.getId(), "상대방 메시지 2");

        ChatMessage deletedMessage = saveMessage(
                secondUser.getId(),
                "삭제된 상대방 메시지"
        );
        deletedMessage.delete();
        chatMessageRepository.saveAndFlush(deletedMessage);

        assertThat(
                chatService.getUnreadMessageCount(
                        firstUser.getId(),
                        chatRoom.getId()
                ).unreadCount()
        ).isEqualTo(2);
    }

    @Test
    void markingMessageAsReadDecreasesUnreadCount() {
        ChatMessage first = saveMessage(
                secondUser.getId(),
                "상대방 메시지 1"
        );
        ChatMessage second = saveMessage(
                secondUser.getId(),
                "상대방 메시지 2"
        );
        saveMessage(secondUser.getId(), "상대방 메시지 3");

        chatService.markChatRoomAsRead(
                firstUser.getId(),
                chatRoom.getId(),
                second.getId()
        );

        ChatRoomMember updatedParticipant = findMember(
                firstParticipant.getId()
        );

        assertThat(updatedParticipant.getLastReadMessageId())
                .isEqualTo(second.getId());

        assertThat(
                chatService.getUnreadMessageCount(
                        firstUser.getId(),
                        chatRoom.getId()
                ).unreadCount()
        ).isEqualTo(1);

        assertThat(first.getId()).isLessThan(second.getId());
    }

    @Test
    void olderReadRequestCannotMoveReadPositionBackward() {
        ChatMessage olderMessage = saveMessage(
                secondUser.getId(),
                "과거 메시지"
        );
        ChatMessage newerMessage = saveMessage(
                secondUser.getId(),
                "최신 메시지"
        );

        chatService.markChatRoomAsRead(
                firstUser.getId(),
                chatRoom.getId(),
                newerMessage.getId()
        );

        chatService.markChatRoomAsRead(
                firstUser.getId(),
                chatRoom.getId(),
                olderMessage.getId()
        );

        assertThat(
                findMember(firstParticipant.getId())
                        .getLastReadMessageId()
        ).isEqualTo(newerMessage.getId());
    }

    @Test
    void concurrentReadRequestsKeepGreatestMessageId() throws Exception {
        ChatMessage olderMessage = saveMessage(
                secondUser.getId(),
                "과거 메시지"
        );
        ChatMessage newerMessage = saveMessage(
                secondUser.getId(),
                "최신 메시지"
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> olderRequest = executor.submit(() -> {
                ready.countDown();
                await(start);
                chatService.markChatRoomAsRead(
                        firstUser.getId(),
                        chatRoom.getId(),
                        olderMessage.getId()
                );
            });

            Future<?> newerRequest = executor.submit(() -> {
                ready.countDown();
                await(start);
                chatService.markChatRoomAsRead(
                        firstUser.getId(),
                        chatRoom.getId(),
                        newerMessage.getId()
                );
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            olderRequest.get(10, TimeUnit.SECONDS);
            newerRequest.get(10, TimeUnit.SECONDS);
        }

        assertThat(
                findMember(firstParticipant.getId())
                        .getLastReadMessageId()
        ).isEqualTo(newerMessage.getId());
    }

    @Test
    void messageFromAnotherChatRoomCannotBeUsedAsReadPosition() {
        ChatRoom anotherRoom = chatRoomRepository.saveAndFlush(
                ChatRoom.createTeamRoom(999L)
        );

        ChatMessage anotherRoomMessage =
                chatMessageRepository.saveAndFlush(
                        ChatMessage.createText(
                                anotherRoom.getId(),
                                secondUser.getId(),
                                "다른 채팅방 메시지"
                        )
                );

        assertThatThrownBy(() ->
                chatService.markChatRoomAsRead(
                        firstUser.getId(),
                        chatRoom.getId(),
                        anotherRoomMessage.getId()
                )
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("해당 채팅방의 메시지가 아닙니다.");

        assertThat(
                findMember(firstParticipant.getId())
                        .getLastReadMessageId()
        ).isNull();
    }

    @Test
    void sendingMessageMovesOnlySenderReadPositionForward() {
        ChatMessage previousMessage = saveMessage(
                secondUser.getId(),
                "상대방이 보낸 이전 메시지"
        );

        ChatMessageResponse sentMessage = chatService.sendMessage(
                firstUser.getId(),
                chatRoom.getId(),
                new SendChatMessageRequest("내가 보낸 메시지")
        );

        assertThat(sentMessage.messageId())
                .isGreaterThan(previousMessage.getId());

        assertThat(
                findMember(firstParticipant.getId())
                        .getLastReadMessageId()
        ).isEqualTo(sentMessage.messageId());

        assertThat(
                findMember(secondParticipant.getId())
                        .getLastReadMessageId()
        ).isNull();
    }

    private ChatMessage saveMessage(Long senderId, String content) {
        return chatMessageRepository.saveAndFlush(
                ChatMessage.createText(
                        chatRoom.getId(),
                        senderId,
                        content
                )
        );
    }

    private ChatRoomMember findMember(Long memberId) {
        return chatRoomMemberRepository.findById(memberId)
                .orElseThrow();
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
