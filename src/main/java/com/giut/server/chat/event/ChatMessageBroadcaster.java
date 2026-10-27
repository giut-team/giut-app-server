package com.giut.server.chat.event;

import com.giut.server.chat.entity.ChatRoomMember;
import com.giut.server.chat.repository.ChatRoomMemberRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ChatMessageBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(ChatMessageEvent event) {
        Long chatRoomId = event.message().chatRoomId();
        List<Long> participantIds = chatRoomMemberRepository.findAllByChatRoomIdAndLeftAtIsNull(chatRoomId)
                .stream().map(ChatRoomMember::getUserId).distinct().toList();
        if (participantIds.isEmpty()) return;
        for (User user : userRepository.findAllByIdInAndStatus(participantIds, User.Status.ACTIVE)) {
            messagingTemplate.convertAndSendToUser(
                    user.getId().toString(), "/queue/chat-rooms/" + chatRoomId, event.message());
        }
    }
}
