package com.giut.server.profile.repository;

import com.giut.server.profile.entity.ProfileScrap;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileScrapRepository extends JpaRepository<ProfileScrap, Long> {
    boolean existsByUser_IdAndProfile_UserId(Long userId, Long profileUserId);
    void deleteByUser_IdAndProfile_UserId(Long userId, Long profileUserId);
}
