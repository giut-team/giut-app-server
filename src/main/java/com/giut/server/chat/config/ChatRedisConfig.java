package com.giut.server.chat.config;

import com.giut.server.chat.event.ChatMessageBroadcaster;
import com.giut.server.chat.event.RedisChatMessageRelay;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.chat.redis.enabled", havingValue = "true")
public class ChatRedisConfig {

    @Bean
    ChannelTopic chatRedisTopic(@Value("${app.chat.redis.channel}") String channel) {
        return new ChannelTopic(channel);
    }

    @Bean
    RedisChatMessageRelay redisChatMessageRelay(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                               ChannelTopic chatRedisTopic, ChatMessageBroadcaster broadcaster) {
        return new RedisChatMessageRelay(redisTemplate, objectMapper, chatRedisTopic, broadcaster);
    }

    @Bean
    RedisMessageListenerContainer chatRedisListenerContainer(RedisConnectionFactory connectionFactory,
                                                            RedisChatMessageRelay relay, ChannelTopic chatRedisTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(relay, chatRedisTopic);
        return container;
    }
}
