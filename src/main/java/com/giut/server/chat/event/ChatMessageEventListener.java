package com.giut.server.chat.event;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatMessageEventListener {

    private final ChatMessageBroadcaster broadcaster;
    private final ObjectProvider<RedisChatMessageRelay> redisRelay;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCommitted(ChatMessageEvent event) {
        RedisChatMessageRelay relay = redisRelay.getIfAvailable();
        if (relay == null) {
            broadcaster.broadcast(event);
            return;
        }

        // The publishing server receives its own event through the same subscription as the other servers.
        relay.publish(event);
    }
}
