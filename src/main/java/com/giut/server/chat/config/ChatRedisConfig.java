package com.giut.server.chat.config;

import com.giut.server.chat.event.ChatMessageBroadcaster;
import com.giut.server.chat.event.RedisChatMessageRelay;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
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
        return singleWorker("chat-redis-publish-");
    }

    @Bean(defaultCandidate = false)
    ThreadPoolTaskExecutor chatRedisReceiveExecutor(MeterRegistry meterRegistry) {
        Counter droppedEvents = Counter.builder("giut.chat.redis.receive.dropped")
                .description("Redis chat events rejected by the receive executor")
                .register(meterRegistry);
        ThreadPoolTaskExecutor executor = singleWorker("chat-redis-receive-");
        // Keep backpressure off the Redis subscription thread and preserve FIFO for queued events.
        executor.setRejectedExecutionHandler((task, pool) -> {
            droppedEvents.increment();
            log.warn("채팅 Redis 수신 대기열이 가득 찼거나 종료되어 이벤트를 전달하지 못했습니다.");
        });
        return executor;
    }

    @Bean(defaultCandidate = false, destroyMethod = "close")
    SimpleAsyncTaskExecutor chatRedisSubscriptionExecutor() {
        return new SimpleAsyncTaskExecutor("chat-redis-subscription-");
    }

    @Bean
    RedisChatMessageRelay redisChatMessageRelay(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                               ChannelTopic chatRedisTopic, ChatMessageBroadcaster broadcaster,
                                               @Qualifier("chatRedisPublishExecutor") TaskExecutor publishExecutor) {
        return new RedisChatMessageRelay(redisTemplate, objectMapper, chatRedisTopic, broadcaster, publishExecutor);
    }

    @Bean
    RedisMessageListenerContainer chatRedisListenerContainer(RedisConnectionFactory connectionFactory,
            RedisChatMessageRelay relay, ChannelTopic chatRedisTopic,
            @Qualifier("chatRedisReceiveExecutor") TaskExecutor receiveExecutor,
            @Qualifier("chatRedisSubscriptionExecutor") TaskExecutor subscriptionExecutor) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(receiveExecutor);
        container.setSubscriptionExecutor(subscriptionExecutor);
        container.setErrorHandler(exception ->
                log.warn("채팅 Redis 구독 이벤트 처리 실패. cause={}", exception.getClass().getSimpleName()));
        container.addMessageListener(relay, chatRedisTopic);
        return container;
    }

    private ThreadPoolTaskExecutor singleWorker(String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix(threadNamePrefix);
        return executor;
    }
}
