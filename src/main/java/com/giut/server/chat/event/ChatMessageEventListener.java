package com.giut.server.chat.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageEventListener {

    private final ChatMessageBroadcaster broadcaster;
    private final ObjectProvider<RedisChatMessageRelay> redisRelay;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCommitted(ChatMessageEvent event) {
        try {
            broadcaster.broadcast(event);
        } catch (RuntimeException exception) {
            log.warn("채팅 로컬 전달 실패. messageId={}, cause={}",
                    event.message().messageId(), exception.getClass().getSimpleName());
        }

        try {
            redisRelay.ifAvailable(relay -> relay.publish(event));
        } catch (RuntimeException exception) {
            log.warn("채팅 Redis 발행 예약 실패. messageId={}, cause={}",
                    event.message().messageId(), exception.getClass().getSimpleName());
        }
    }
}
