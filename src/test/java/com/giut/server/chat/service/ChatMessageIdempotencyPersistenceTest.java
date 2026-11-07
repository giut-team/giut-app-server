package com.giut.server.chat.service;

import com.giut.server.chat.dto.request.SendChatMessageRequest;
import com.giut.server.chat.dto.response.ChatMessageResponse;
import com.giut.server.chat.entity.ChatRoom;
import com.giut.server.chat.entity.ChatRoomMember;
import com.giut.server.chat.event.ChatMessageEvent;
import com.giut.server.chat.repository.ChatMessageRepository;
import com.giut.server.chat.repository.ChatRoomMemberRepository;
import com.giut.server.chat.repository.ChatRoomRepository;
import com.giut.server.global.exception.ConflictException;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat_idempotency_test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
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
@Import(ChatMessageIdempotencyPersistenceTest.ChatMessageEventCounter.class)
class ChatMessageIdempotencyPersistenceTest {

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

    @Autowired
    private ChatMessageEventCounter eventCounter;

    private User firstUser;
    private User secondUser;
    private ChatRoom chatRoom;

    @BeforeEach
    void setUp() {
        eventCounter.reset();

        firstUser = userRepository.saveAndFlush(
                User.createOAuthUser(
                        "idempotency-first@example.com",
                        "첫 번째 사용자",
                        User.OAuthProvider.KAKAO,
                        "idempotency-first-oauth-id"
                )
        );
        secondUser = userRepository.saveAndFlush(
                User.createOAuthUser(
                        "idempotency-second@example.com",
                        "두 번째 사용자",
                        User.OAuthProvider.KAKAO,
                        "idempotency-second-oauth-id"
                )
        );
        chatRoom = chatRoomRepository.saveAndFlush(
                ChatRoom.createPersonalRoom(firstUser.getId(), secondUser.getId())
        );
        chatRoomMemberRepository.saveAndFlush(
                ChatRoomMember.join(chatRoom.getId(), firstUser.getId())
        );
        chatRoomMemberRepository.saveAndFlush(
                ChatRoomMember.join(chatRoom.getId(), secondUser.getId())
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
    void sameRequestReturnsExistingMessageWithoutDuplicateStorageOrEvent() {
        String clientMessageId = UUID.randomUUID().toString();
        SendChatMessageRequest request = new SendChatMessageRequest(
                clientMessageId,
                "재시도할 메시지"
        );

        ChatMessageResponse first = chatService.sendMessage(
                firstUser.getId(),
                chatRoom.getId(),
                request
        );
        ChatMessageResponse retried = chatService.sendMessage(
                firstUser.getId(),
                chatRoom.getId(),
                request
        );

        assertThat(retried.messageId()).isEqualTo(first.messageId());
        assertThat(retried.clientMessageId()).isEqualTo(clientMessageId);
        assertThat(chatMessageRepository.count()).isEqualTo(1);
        assertThat(eventCounter.count()).isEqualTo(1);
    }

    @Test
    void concurrentSameRequestsCreateOnlyOneMessage() throws Exception {
        int requestCount = 10;
        String clientMessageId = UUID.randomUUID().toString();
        SendChatMessageRequest request = new SendChatMessageRequest(
                clientMessageId,
                "동시에 전송할 메시지"
        );
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<ChatMessageResponse>> futures = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return chatService.sendMessage(
                            firstUser.getId(),
                            chatRoom.getId(),
                            request
                    );
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Long> messageIds = new ArrayList<>();
            for (Future<ChatMessageResponse> future : futures) {
                messageIds.add(future.get(15, TimeUnit.SECONDS).messageId());
            }

            assertThat(messageIds).hasSize(requestCount).containsOnly(messageIds.get(0));
            assertThat(chatMessageRepository.count()).isEqualTo(1);
            assertThat(eventCounter.count()).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void sameClientMessageIdCannotBeReusedForDifferentContent() {
        String clientMessageId = UUID.randomUUID().toString();
        chatService.sendMessage(
                firstUser.getId(),
                chatRoom.getId(),
                new SendChatMessageRequest(clientMessageId, "원본 메시지")
        );

        assertThatThrownBy(() -> chatService.sendMessage(
                firstUser.getId(),
                chatRoom.getId(),
                new SendChatMessageRequest(clientMessageId, "변경된 메시지")
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("동일한 clientMessageId를 다른 메시지에 재사용할 수 없습니다.");

        assertThat(chatMessageRepository.count()).isEqualTo(1);
        assertThat(eventCounter.count()).isEqualTo(1);
    }

    @Test
    void differentSendersMayUseTheSameClientMessageId() {
        String clientMessageId = UUID.randomUUID().toString();

        ChatMessageResponse first = chatService.sendMessage(
                firstUser.getId(),
                chatRoom.getId(),
                new SendChatMessageRequest(clientMessageId, "첫 번째 사용자 메시지")
        );
        ChatMessageResponse second = chatService.sendMessage(
                secondUser.getId(),
                chatRoom.getId(),
                new SendChatMessageRequest(clientMessageId, "두 번째 사용자 메시지")
        );

        assertThat(first.messageId()).isNotEqualTo(second.messageId());
        assertThat(chatMessageRepository.count()).isEqualTo(2);
        assertThat(eventCounter.count()).isEqualTo(2);
    }

    static class ChatMessageEventCounter {

        private final AtomicInteger count = new AtomicInteger();

        @EventListener
        public void record(ChatMessageEvent ignored) {
            count.incrementAndGet();
        }

        int count() {
            return count.get();
        }

        void reset() {
            count.set(0);
        }
    }
}
