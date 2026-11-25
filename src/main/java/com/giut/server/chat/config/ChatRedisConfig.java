package com.giut.server.chat.config;

import com.giut.server.chat.event.ChatMessageBroadcaster;
import com.giut.server.chat.event.RedisChatMessageRelay;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.Assert;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.chat.redis.enabled", havingValue = "true")
public class ChatRedisConfig {

    @Bean
    ChannelTopic chatRedisTopic(@Value("${app.chat.redis.channel:}") String channel) {
        Assert.hasText(channel, "채팅 Redis 활성화 시 CHAT_REDIS_CHANNEL을 지정해야 합니다.");
        return new ChannelTopic(channel.trim());
    }

    @Bean(defaultCandidate = false)
    ThreadPoolTaskExecutor chatRedisPublishExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("chat-redis-publish-");
        return executor;
    }

    @Bean
    RedisChatMessageRelay redisChatMessageRelay(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                               ChannelTopic chatRedisTopic, ChatMessageBroadcaster broadcaster,
                                               @Qualifier("chatRedisPublishExecutor") TaskExecutor publishExecutor) {
        return new RedisChatMessageRelay(redisTemplate, objectMapper, chatRedisTopic, broadcaster, publishExecutor);
    }

    @Bean
    RedisMessageListenerContainer chatRedisListenerContainer(RedisConnectionFactory connectionFactory,
                                                            RedisChatMessageRelay relay, ChannelTopic chatRedisTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setErrorHandler(exception ->
                log.warn("채팅 Redis 구독 이벤트 처리 실패. cause={}", exception.getClass().getSimpleName()));
        container.addMessageListener(relay, chatRedisTopic);
        return container;
    }
}
