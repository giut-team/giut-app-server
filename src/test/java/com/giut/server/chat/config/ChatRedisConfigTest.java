package com.giut.server.chat.config;

import com.giut.server.chat.event.ChatMessageBroadcaster;
import com.giut.server.chat.event.RedisChatMessageRelay;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.LifecycleProcessor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChatRedisConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ChatRedisConfig.class)
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(ChatMessageBroadcaster.class, () -> mock(ChatMessageBroadcaster.class))
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
            .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
            // Inspect the actual beans without starting a Redis subscription in the unit test.
            .withBean("lifecycleProcessor", LifecycleProcessor.class, () -> mock(LifecycleProcessor.class));

    @Test
    void disabledModeDoesNotRegisterTheRedisRelayOrListener() {
        runner.withPropertyValues("app.chat.redis.enabled=false").run(context ->
                assertThat(context)
                        .doesNotHaveBean(RedisChatMessageRelay.class)
                        .doesNotHaveBean(RedisMessageListenerContainer.class)
                        .doesNotHaveBean("chatRedisPublishExecutor")
                        .doesNotHaveBean("chatRedisReceiveExecutor"));
    }

    @Test
    void receiveQueueOverflowDropsTheTaskAndIncrementsTheCounter() {
        runner.withPropertyValues("app.chat.redis.enabled=true", "app.chat.redis.channel=giut:chat:test:messages:v1")
                .run(context -> {
                    var receiver = context.getBean("chatRedisReceiveExecutor", ThreadPoolTaskExecutor.class);
                    var dropped = context.getBean(SimpleMeterRegistry.class)
                            .get("giut.chat.redis.receive.dropped").counter();
                    var started = new CountDownLatch(1);
                    var release = new CountDownLatch(1);
                    var drained = new CountDownLatch(receiver.getQueueCapacity());
                    var rejectedTaskExecuted = new CountDownLatch(1);

                    receiver.execute(() -> {
                        started.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        }
                    });

                    try {
                        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                        for (int i = 0; i < receiver.getQueueCapacity(); i++) {
                            receiver.execute(drained::countDown);
                        }
                        assertThat(dropped.count()).isZero();
                        receiver.execute(rejectedTaskExecuted::countDown);
                        assertThat(dropped.count()).isEqualTo(1);
                    } finally {
                        release.countDown();
                    }
                    assertThat(drained.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThat(rejectedTaskExecuted.getCount()).isEqualTo(1);
                });
    }

    @Test
    void enabledModeRequiresAnExplicitChannel() {
        runner.withPropertyValues("app.chat.redis.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "채팅 Redis 활성화 시 CHAT_REDIS_CHANNEL을 지정해야 합니다.");
        });
    }

    @Test
    void enabledModeRegistersTheRelayAndListenerForTheConfiguredChannel() {
        runner.withPropertyValues("app.chat.redis.enabled=true", "app.chat.redis.channel=giut:chat:test:messages:v1")
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisChatMessageRelay.class)
                            .hasSingleBean(RedisMessageListenerContainer.class)
                            .hasBean("chatRedisPublishExecutor");
                    var container = context.getBean(RedisMessageListenerContainer.class);
                    var receiver = context.getBean("chatRedisReceiveExecutor", ThreadPoolTaskExecutor.class);
                    assertThat(ReflectionTestUtils.getField(container, "taskExecutor")).isSameAs(receiver);
                    assertThat(ReflectionTestUtils.getField(container, "subscriptionExecutor"))
                            .isSameAs(context.getBean("chatRedisSubscriptionExecutor"));
                    assertThat(receiver.getCorePoolSize()).isEqualTo(1);
                    assertThat(receiver.getMaxPoolSize()).isEqualTo(1);
                    assertThat(receiver.getQueueCapacity()).isEqualTo(256);
                    assertThat(context.getBean(ChannelTopic.class).getTopic()).isEqualTo("giut:chat:test:messages:v1");
                });
    }
}
