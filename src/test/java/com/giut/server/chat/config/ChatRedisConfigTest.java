package com.giut.server.chat.config;

import com.giut.server.chat.event.ChatMessageBroadcaster;
import com.giut.server.chat.event.RedisChatMessageRelay;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.LifecycleProcessor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChatRedisConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ChatRedisConfig.class)
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(ChatMessageBroadcaster.class, () -> mock(ChatMessageBroadcaster.class))
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
            // Inspect the actual beans without starting a Redis subscription in the unit test.
            .withBean("lifecycleProcessor", LifecycleProcessor.class, () -> mock(LifecycleProcessor.class));

    @Test
    void disabledModeDoesNotRegisterTheRedisRelayOrListener() {
        runner.withPropertyValues("app.chat.redis.enabled=false").run(context ->
                assertThat(context)
                        .doesNotHaveBean(RedisChatMessageRelay.class)
                        .doesNotHaveBean(RedisMessageListenerContainer.class)
                        .doesNotHaveBean("chatRedisPublishExecutor"));
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
                    assertThat(context.getBean(ChannelTopic.class).getTopic()).isEqualTo("giut:chat:test:messages:v1");
                });
    }
}
