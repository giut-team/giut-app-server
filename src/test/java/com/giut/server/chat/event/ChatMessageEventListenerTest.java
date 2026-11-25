package com.giut.server.chat.event;

import com.giut.server.chat.dto.response.ChatMessageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskRejectedException;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatMessageEventListenerTest {

    @Mock
    private ChatMessageBroadcaster broadcaster;

    @Mock
    private ObjectProvider<RedisChatMessageRelay> redisRelay;

    @InjectMocks
    private ChatMessageEventListener listener;

    private final ChatMessageEvent event = new ChatMessageEvent(mock(ChatMessageResponse.class));

    @Test
    void redisModeDeliversLocallyAndPublishesForOtherServers() {
        RedisChatMessageRelay relay = enableRedis();

        listener.onMessageCommitted(event);

        verify(broadcaster).broadcast(event);
        verify(relay).publish(event);
    }

    @Test
    void localModeBroadcastsWhenRedisIsDisabled() {
        listener.onMessageCommitted(event);

        verify(broadcaster).broadcast(event);
    }

    @Test
    void publishQueueRejectionDoesNotUndoLocalDeliveryOrFailTheRequest() {
        RedisChatMessageRelay relay = enableRedis();
        doThrow(new TaskRejectedException("queue full")).when(relay).publish(event);

        assertThatCode(() -> listener.onMessageCommitted(event)).doesNotThrowAnyException();

        verify(broadcaster).broadcast(event);
    }

    @Test
    void localFailureDoesNotPreventPublishingForOtherServers() {
        RedisChatMessageRelay relay = enableRedis();
        doThrow(new IllegalStateException()).when(broadcaster).broadcast(event);

        assertThatCode(() -> listener.onMessageCommitted(event)).doesNotThrowAnyException();

        verify(relay).publish(event);
    }

    private RedisChatMessageRelay enableRedis() {
        RedisChatMessageRelay relay = mock(RedisChatMessageRelay.class);
        doAnswer(invocation -> {
            Consumer<RedisChatMessageRelay> callback = invocation.getArgument(0);
            callback.accept(relay);
            return null;
        }).when(redisRelay).ifAvailable(any());
        return relay;
    }
}
