package com.giut.server.user.service;

import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.user.dto.response.BlockedUserListResponse;
import com.giut.server.user.dto.response.UserBlockResponse;
import com.giut.server.user.entity.User;
import com.giut.server.user.entity.UserBlock;
import com.giut.server.user.repository.UserBlockRepository;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserBlockService {
    private final UserBlockRepository blocks;
    private final UserRepository users;

    @Transactional
    public UserBlockResponse block(Long currentUserId, Long targetUserId) {
        validateDistinct(currentUserId, targetUserId);
        // Match recommendation creation's ascending user-lock order to serialize concurrent writes.
        User first = lockUser(Math.min(currentUserId, targetUserId));
        User second = lockUser(Math.max(currentUserId, targetUserId));
        User actor = first.getId().equals(currentUserId) ? first : second;
        User target = first.getId().equals(targetUserId) ? first : second;
        requireActive(actor);
        requireActive(target);
        if (!blocks.existsByBlocker_IdAndBlockedUser_Id(currentUserId, targetUserId)) {
            blocks.saveAndFlush(UserBlock.create(actor, target));
        }
        return new UserBlockResponse(targetUserId, true);
    }

    @Transactional
    public UserBlockResponse unblock(Long currentUserId, Long targetUserId) {
        validateDistinct(currentUserId, targetUserId);
        User first = lockUser(Math.min(currentUserId, targetUserId));
        User second = lockUser(Math.max(currentUserId, targetUserId));
        requireActive(first.getId().equals(currentUserId) ? first : second);
        // An inactive target can still be unblocked. Never remove the other user's block.
        blocks.deleteByBlocker_IdAndBlockedUser_Id(currentUserId, targetUserId);
        blocks.flush();
        return new UserBlockResponse(targetUserId, false);
    }

    @Transactional(readOnly = true)
    public UserBlockResponse getStatus(Long currentUserId, Long targetUserId) {
        requireActiveUser(currentUserId);
        return new UserBlockResponse(targetUserId,
                blocks.existsByBlocker_IdAndBlockedUser_Id(currentUserId, targetUserId));
    }

    @Transactional(readOnly = true)
    public BlockedUserListResponse getBlockedUsers(Long currentUserId, int page) {
        requireActiveUser(currentUserId);
        if (page < 0 || page > Integer.MAX_VALUE / 20) throw new IllegalArgumentException("page 범위가 올바르지 않습니다.");
        var result = blocks.findAllByBlocker_Id(currentUserId,
                PageRequest.of(page, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        var entries = result.getContent().stream().map(block -> new BlockedUserListResponse.BlockedUser(
                block.getBlockedUser().getId(), block.getBlockedUser().getNickname(), block.getCreatedAt())).toList();
        return new BlockedUserListResponse(entries, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    @Transactional(readOnly = true)
    public void requireProfilesVisible(Long viewerId, Long targetUserId) {
        // Anonymous shared cards retain their existing limited token-only access policy.
        if (viewerId != null && !viewerId.equals(targetUserId) && blocks.existsBetween(viewerId, targetUserId)) {
            throw new ResourceNotFoundException("공개 프로필을 찾을 수 없습니다.");
        }
    }

    private void validateDistinct(Long first, Long second) {
        if (first.equals(second)) throw new IllegalArgumentException("자기 자신을 차단할 수 없습니다.");
    }

    private User lockUser(Long id) {
        return users.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
    }

    private void requireActiveUser(Long id) {
        requireActive(users.findById(id).orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다.")));
    }

    private void requireActive(User user) {
        if (user.getStatus() != User.Status.ACTIVE) throw new ResourceNotFoundException("사용자를 찾을 수 없습니다.");
    }
}
