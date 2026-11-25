package com.giut.server.chat.event;

import com.giut.server.chat.dto.response.ChatMessageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageEventListenerTest {

    @Mock
    private ChatMessageBroadcaster broadcaster;

    @Mock
    private ObjectProvider<RedisChatMessageRelay> redisRelay;

    @InjectMocks
    private ChatMessageEventListener listener;

    @Test
    void redisModePublishesWithoutAlsoBroadcastingLocally() {
        RedisChatMessageRelay relay = mock(RedisChatMessageRelay.class);
        when(redisRelay.getIfAvailable()).thenReturn(relay);
        ChatMessageEvent event = new ChatMessageEvent(mock(ChatMessageResponse.class));

        listener.onMessageCommitted(event);

        verify(relay).publish(event);
        verifyNoInteractions(broadcaster);
    }

    @Test
    void localModeBroadcastsWhenRedisIsDisabled() {
        ChatMessageEvent event = new ChatMessageEvent(mock(ChatMessageResponse.class));

        listener.onMessageCommitted(event);

        verify(broadcaster).broadcast(event);
    }
}
