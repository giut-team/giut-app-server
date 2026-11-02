package com.giut.server.chat.service;

import com.giut.server.chat.dto.request.CreatePersonalChatRoomRequest;
import com.giut.server.chat.dto.response.ChatRoomResponse;
import com.giut.server.chat.entity.ChatRoomMember;
import com.giut.server.chat.repository.ChatRoomMemberRepository;
import com.giut.server.chat.repository.ChatRoomRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:personal_chat_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class PersonalChatRoomPersistenceTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    private User firstUser;
    private User secondUser;

    @BeforeEach
    void setUp() {
        firstUser = userRepository.saveAndFlush(
                User.createOAuthUser(
                        "first@example.com",
                        "첫 번째 사용자",
                        User.OAuthProvider.KAKAO,
                        "first-oauth-id"
                )
        );

        secondUser = userRepository.saveAndFlush(
                User.createOAuthUser(
                        "second@example.com",
                        "두 번째 사용자",
                        User.OAuthProvider.KAKAO,
                        "second-oauth-id"
                )
        );
    }

    @AfterEach
    void cleanUp() {
        chatRoomMemberRepository.deleteAll();
        chatRoomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void userOrderDoesNotCreateAnotherPersonalChatRoom() {
        ChatRoomResponse first = chatService.createPersonalChatRoom(
                firstUser.getId(),
                new CreatePersonalChatRoomRequest(secondUser.getId())
        );

        ChatRoomResponse second = chatService.createPersonalChatRoom(
                secondUser.getId(),
                new CreatePersonalChatRoomRequest(firstUser.getId())
        );

        assertThat(second.chatRoomId())
                .isEqualTo(first.chatRoomId());

        assertThat(chatRoomRepository.count()).isEqualTo(1);
        assertThat(
                chatRoomMemberRepository
                        .findAllByChatRoomIdAndLeftAtIsNull(
                                first.chatRoomId()
                        )
        ).hasSize(2);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
    }

    @Test
    void reusesExistingRoomAfterParticipantLeaves() {
        ChatRoomResponse created = chatService.createPersonalChatRoom(
                firstUser.getId(),
                new CreatePersonalChatRoomRequest(secondUser.getId())
        );

        chatService.leaveChatRoom(
                firstUser.getId(),
                created.chatRoomId()
        );

        ChatRoomResponse reopened = chatService.createPersonalChatRoom(
                firstUser.getId(),
                new CreatePersonalChatRoomRequest(secondUser.getId())
        );

        assertThat(reopened.chatRoomId())
                .isEqualTo(created.chatRoomId());

        assertThat(chatRoomRepository.count()).isEqualTo(1);

        ChatRoomMember firstMember = chatRoomMemberRepository
                .findByChatRoomIdAndUserId(
                        created.chatRoomId(),
                        firstUser.getId()
                )
                .orElseThrow();

        assertThat(firstMember.getLeftAt()).isNull();
    }

    @Test
    void concurrentRequestsCreateOnlyOneRoom() throws Exception {
        int requestCount = 10;

        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor =
                     Executors.newFixedThreadPool(requestCount)) {

            List<Future<ChatRoomResponse>> futures =
                    IntStream.range(0, requestCount)
                            .mapToObj(index -> executor.submit(() -> {
                                ready.countDown();
                                start.await();

                                return chatService.createPersonalChatRoom(
                                        firstUser.getId(),
                                        new CreatePersonalChatRoomRequest(
                                                secondUser.getId()
                                        )
                                );
                            }))
                            .toList();

            assertThat(
                    ready.await(5, TimeUnit.SECONDS)
            ).isTrue();

            start.countDown();

            List<ChatRoomResponse> responses =
                    new ArrayList<>();

            for (Future<ChatRoomResponse> future : futures) {
                responses.add(
                        future.get(10, TimeUnit.SECONDS)
                );
            }

            assertThat(
                    responses.stream()
                            .map(ChatRoomResponse::chatRoomId)
                            .distinct()
                            .count()
            ).isEqualTo(1);

            assertThat(chatRoomRepository.count()).isEqualTo(1);

            Long chatRoomId = responses.getFirst().chatRoomId();

            assertThat(
                    chatRoomMemberRepository
                            .findAllByChatRoomIdAndLeftAtIsNull(
                                    chatRoomId
                            )
            ).hasSize(2);
        }
    }
}
