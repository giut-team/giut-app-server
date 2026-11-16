package com.giut.server.chat.service;

import com.giut.server.chat.dto.response.ChatMessageResponse;
import com.giut.server.chat.entity.ChatMessage;
import com.giut.server.chat.event.ChatMessageEvent;
import com.giut.server.chat.repository.ChatMessageRepository;
import com.giut.server.chat.repository.ChatRoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageCreator {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatMessageResponse create(
            Long chatRoomId,
            Long senderId,
            Long participantId,
            String clientMessageId,
            String content
    ) {
        ChatMessage message = chatMessageRepository.saveAndFlush(
                ChatMessage.createText(
                        chatRoomId,
                        senderId,
                        clientMessageId,
                        content
                )
        );

        chatRoomMemberRepository.updateLastReadMessageIdIfGreater(
                participantId,
                message.getId()
        );

        ChatMessageResponse response = ChatMessageResponse.from(message);
        eventPublisher.publishEvent(new ChatMessageEvent(response));
        return response;
    }
}
