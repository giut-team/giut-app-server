package com.giut.server.service;

import com.giut.server.entity.ChatRoomMember;
import com.giut.server.repository.ChatRoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatMessageBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(ChatMessageEvent event) {
        Long chatRoomId = event.message().chatRoomId();
        for (ChatRoomMember member : chatRoomMemberRepository.findAllByChatRoomIdAndLeftAtIsNull(chatRoomId)) {
            messagingTemplate.convertAndSendToUser(
                    member.getUserId().toString(), "/queue/chat-rooms/" + chatRoomId, event.message());
        }
    }
}
