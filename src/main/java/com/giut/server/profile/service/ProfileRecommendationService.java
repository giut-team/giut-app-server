package com.giut.server.profile.service;

import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.profile.dto.response.ProfileRecommendationResponse;
import com.giut.server.profile.entity.ProfileRecommendation;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.repository.ProfileRecommendationRepository;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileRecommendationService {
    private final ProfileRecommendationRepository recommendationRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;

    @Transactional
    public ProfileRecommendationResponse recommend(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("본인 프로필은 추천할 수 없습니다.");
        }
        // 두 사용자를 ID 순서로 잠가 중복 요청과 서로를 동시에 추천할 때의 잠금 역전을 방지한다.
        User recommender;
        if (currentUserId < targetUserId) {
            recommender = findActiveUserForUpdate(currentUserId);
            lockTargetUser(targetUserId);
        } else {
            lockTargetUser(targetUserId);
            recommender = findActiveUserForUpdate(currentUserId);
        }
        User target = findPublicProfileUser(targetUserId);
        if (!recommendationRepository.existsByRecommender_IdAndRecommendedUser_Id(currentUserId, targetUserId)) {
            recommendationRepository.saveAndFlush(ProfileRecommendation.create(recommender, target));
        }
        return response(currentUserId, targetUserId);
    }

    @Transactional
    public ProfileRecommendationResponse cancelRecommendation(Long currentUserId, Long targetUserId) {
        findActiveUserForUpdate(currentUserId);
        // 공개 설정을 해제한 대상에게도 자신의 추천을 취소할 수 있다.
        recommendationRepository.deleteByRecommender_IdAndRecommendedUser_Id(currentUserId, targetUserId);
        recommendationRepository.flush();
        return response(currentUserId, targetUserId);
    }

    @Transactional(readOnly = true)
    public ProfileRecommendationResponse getRecommendation(Long currentUserId, Long targetUserId) {
        userRepository.findById(currentUserId)
                .filter(user -> user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("추천 상태를 조회할 수 없는 사용자입니다."));
        findPublicProfileUser(targetUserId);
        return response(currentUserId, targetUserId);
    }

    private ProfileRecommendationResponse response(Long currentUserId, Long targetUserId) {
        return new ProfileRecommendationResponse(targetUserId,
                recommendationRepository.existsByRecommender_IdAndRecommendedUser_Id(currentUserId, targetUserId),
                recommendationRepository.countByRecommendedUser_Id(targetUserId));
    }

    private User findActiveUserForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .filter(user -> user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("추천할 수 없는 사용자입니다."));
    }

    private void lockTargetUser(Long userId) {
        userRepository.findByIdForUpdate(userId)
                .filter(user -> user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("공개 프로필을 찾을 수 없습니다."));
    }

    private User findPublicProfileUser(Long userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .filter(UserProfile::isSearchable)
                .filter(found -> found.getActivityStatus() != UserProfile.ActivityStatus.RESTING)
                .orElseThrow(() -> new ResourceNotFoundException("공개 프로필을 찾을 수 없습니다."));
        if (profile.getUser().getStatus() != User.Status.ACTIVE) {
            throw new ResourceNotFoundException("공개 프로필을 찾을 수 없습니다.");
        }
        return profile.getUser();
    }
}
