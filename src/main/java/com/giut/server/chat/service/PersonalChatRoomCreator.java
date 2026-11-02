package com.giut.server.chat.service;


import com.giut.server.chat.entity.ChatRoom;
import com.giut.server.chat.entity.ChatRoomMember;
import com.giut.server.chat.repository.ChatRoomMemberRepository;
import com.giut.server.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PersonalChatRoomCreator {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatRoom create(
            Long userId,
            Long targetUserId
    ) {

        ChatRoom chatRoom = chatRoomRepository.saveAndFlush(
                ChatRoom.createPersonalRoom(
                        userId,
                        targetUserId
                )
        );

        chatRoomMemberRepository.save(
                ChatRoomMember.join(chatRoom.getId(), userId)
        );

        chatRoomMemberRepository.save(
                ChatRoomMember.join(
                        chatRoom.getId(),
                        targetUserId
                )
        );

        return chatRoom;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreParticipants(
            Long chatRoomId,
            Long userId,
            Long targetUserId
    ) {
        restoreParticipant(chatRoomId, userId);
        restoreParticipant(chatRoomId, targetUserId);
    }

    private void restoreParticipant(
            Long chatRoomId,
            Long userId
    ) {
        chatRoomMemberRepository
                .findByChatRoomIdAndUserId(chatRoomId, userId)
                .ifPresentOrElse(
                        ChatRoomMember::rejoin,
                        () -> chatRoomMemberRepository.save(
                                ChatRoomMember.join(chatRoomId, userId)
                        )
                );
    }
}
