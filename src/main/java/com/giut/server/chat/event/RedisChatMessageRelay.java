package com.giut.server.chat.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class RedisChatMessageRelay implements MessageListener {

    private final String nodeId = UUID.randomUUID().toString();
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChannelTopic topic;
    private final ChatMessageBroadcaster broadcaster;
    private final TaskExecutor publishExecutor;

    public void publish(ChatMessageEvent event) {
        publishExecutor.execute(() -> publishToRedis(event));
    }

    private void publishToRedis(ChatMessageEvent event) {
        try {
            RedisEvent redisEvent = new RedisEvent(nodeId, event);
            redisTemplate.convertAndSend(topic.getTopic(), objectMapper.writeValueAsString(redisEvent));
        } catch (RuntimeException exception) {
            // The origin already delivered locally. An ambiguous publish must not be retried.
            log.warn("채팅 Redis 발행 실패. messageId={}, cause={}",
                    event.message().messageId(), exception.getClass().getSimpleName());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        RedisEvent redisEvent;
        try {
            redisEvent = objectMapper.readValue(message.getBody(), RedisEvent.class);
        } catch (JacksonException exception) {
            log.warn("채팅 Redis 이벤트 역직렬화 실패. cause={}", exception.getClass().getSimpleName());
            return;
        }
        if (redisEvent == null || !StringUtils.hasText(redisEvent.origin()) || redisEvent.event() == null
                || redisEvent.event().message() == null || redisEvent.event().message().chatRoomId() == null
                || redisEvent.event().message().messageId() == null) {
            log.warn("채팅 Redis 이벤트에 필수 정보가 없습니다.");
            return;
        }
        if (nodeId.equals(redisEvent.origin())) {
            return;
        }
        try {
            broadcaster.broadcast(redisEvent.event());
        } catch (RuntimeException exception) {
            log.warn("채팅 Redis 수신 이벤트 전달 실패. messageId={}, cause={}",
                    redisEvent.event().message().messageId(), exception.getClass().getSimpleName());
        }
    }

    record RedisEvent(String origin, ChatMessageEvent event) {
    }
}
