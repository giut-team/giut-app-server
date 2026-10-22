package com.giut.server.profile.service;

import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.profile.dto.response.ProfileScrapResponse;
import com.giut.server.profile.entity.ProfileScrap;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.repository.ProfileScrapRepository;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileScrapService {
    private final ProfileScrapRepository scraps;
    private final UserRepository users;
    private final UserProfileRepository profiles;

    @Transactional(readOnly = true)
    public ProfileScrapResponse getStatus(Long userId, Long profileUserId) {
        return new ProfileScrapResponse(profileUserId,
                scraps.existsByUser_IdAndProfile_UserId(userId, profileUserId));
    }

    @Transactional
    public ProfileScrapResponse add(Long userId, Long profileUserId) {
        User user = activeUserForUpdate(userId);
        if (userId.equals(profileUserId)) {
            throw new IllegalArgumentException("본인 프로필은 관심 등록할 수 없습니다.");
        }
        UserProfile profile = profiles.findById(profileUserId)
                .filter(UserProfile::isSearchable)
                .filter(found -> found.getActivityStatus() != UserProfile.ActivityStatus.RESTING)
                .filter(found -> found.getUser().getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("공개 프로필을 찾을 수 없습니다."));
        if (!scraps.existsByUser_IdAndProfile_UserId(userId, profileUserId)) {
            scraps.save(ProfileScrap.create(user, profile));
        }
        return new ProfileScrapResponse(profileUserId, true);
    }

    @Transactional
    public ProfileScrapResponse remove(Long userId, Long profileUserId) {
        activeUserForUpdate(userId);
        // 공개 상태가 바뀐 프로필도 저장한 사용자는 해제할 수 있다.
        scraps.deleteByUser_IdAndProfile_UserId(userId, profileUserId);
        return new ProfileScrapResponse(profileUserId, false);
    }

    private User activeUserForUpdate(Long userId) {
        // 같은 사용자의 추가/해제를 직렬화하고 unique 제약을 최종 방어선으로 둔다.
        return users.findByIdForUpdate(userId)
                .filter(found -> found.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
    }
}
