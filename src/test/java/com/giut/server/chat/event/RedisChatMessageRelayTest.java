package com.giut.server.chat.event;

import com.giut.server.chat.dto.response.ChatMessageResponse;
import com.giut.server.chat.entity.ChatMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisChatMessageRelayTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ChannelTopic channel = new ChannelTopic("giut:chat:test:messages:v1");
    private final ChatMessageBroadcaster local = mock(ChatMessageBroadcaster.class);
    private final ChatMessageBroadcaster remote = mock(ChatMessageBroadcaster.class);
    private final RedisChatMessageRelay publisher = relay(local);

    private final ChatMessageEvent event = new ChatMessageEvent(new ChatMessageResponse(
            30L, 5L, 12L, "550e8400-e29b-41d4-a716-446655440000", "왕복 테스트",
            ChatMessage.MessageType.TEXT, ChatMessage.Status.ACTIVE, Instant.parse("2026-11-25T01:00:00.123456Z")));

    @Test
    void eventRoundTripsThroughJacksonAndOnlyTheOtherNodeDeliversIt() {
        publisher.publish(event);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(channel.getTopic()), payload.capture());

        DefaultMessage message = message(payload.getValue());
        publisher.onMessage(message, null);
        relay(remote).onMessage(message, null);

        verifyNoInteractions(local);
        verify(remote).broadcast(event);
    }

    @Test
    void redisFailureDoesNotPropagateFromThePublisherOrTriggerLocalDeliveryAgain() {
        when(redis.convertAndSend(eq(channel.getTopic()), anyString()))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();

        verify(redis).convertAndSend(eq(channel.getTopic()), anyString());
        verifyNoInteractions(local);
    }

    @Test
    void deliveryFailureDoesNotEscapeTheListenerOrPreventTheNextEvent() {
        RedisChatMessageRelay receiver = relay(remote);
        DefaultMessage incoming = message(mapper.writeValueAsString(
                new RedisChatMessageRelay.RedisEvent("another-node", event)));
        doThrow(new IllegalStateException("DB unavailable")).doNothing().when(remote).broadcast(event);

        assertThatCode(() -> receiver.onMessage(incoming, null)).doesNotThrowAnyException();
        receiver.onMessage(incoming, null);

        verify(remote, times(2)).broadcast(event);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "{\"origin\":\"other\",\"event\":{\"message\":{\"messageId\":30}}}"})
    void ignoresEventsWithoutAValidMessageAndRoom(String json) {
        assertThatCode(() -> publisher.onMessage(message(json), null)).doesNotThrowAnyException();

        verifyNoInteractions(local);
    }

    private RedisChatMessageRelay relay(ChatMessageBroadcaster broadcaster) {
        return new RedisChatMessageRelay(redis, mapper, channel, broadcaster, new SyncTaskExecutor());
    }

    private DefaultMessage message(String json) {
        return new DefaultMessage(channel.getTopic().getBytes(StandardCharsets.UTF_8),
                json.getBytes(StandardCharsets.UTF_8));
    }
}
