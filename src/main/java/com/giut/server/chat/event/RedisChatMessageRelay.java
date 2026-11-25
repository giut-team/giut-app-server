package com.giut.server.chat.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@RequiredArgsConstructor
public class RedisChatMessageRelay implements MessageListener {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChannelTopic topic;
    private final ChatMessageBroadcaster broadcaster;

    public void publish(ChatMessageEvent event) {
        try {
            redisTemplate.convertAndSend(topic.getTopic(), objectMapper.writeValueAsString(event));
        } catch (DataAccessException | JacksonException exception) {
            // The database write already committed. Retrying an ambiguous publish can duplicate delivery.
            log.warn("채팅 Redis 발행 실패. messageId={}, cause={}",
                    event.message().messageId(), exception.getClass().getSimpleName());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        ChatMessageEvent event;
        try {
            event = objectMapper.readValue(message.getBody(), ChatMessageEvent.class);
        } catch (JacksonException exception) {
            log.warn("채팅 Redis 이벤트 역직렬화 실패. cause={}", exception.getClass().getSimpleName());
            return;
        }
        if (event == null || event.message() == null || event.message().chatRoomId() == null) {
            log.warn("채팅 Redis 이벤트에 채팅방 정보가 없습니다.");
            return;
        }
        broadcaster.broadcast(event);
    }
}
