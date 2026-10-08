package com.giut.server.profile.repository;

import com.giut.server.profile.entity.ProfileRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRecommendationRepository extends JpaRepository<ProfileRecommendation, Long> {
    boolean existsByRecommender_IdAndRecommendedUser_Id(Long recommenderId, Long recommendedUserId);
    long countByRecommendedUser_Id(Long userId);
    void deleteByRecommender_IdAndRecommendedUser_Id(Long recommenderId, Long recommendedUserId);
}
