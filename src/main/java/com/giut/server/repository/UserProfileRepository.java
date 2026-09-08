package com.giut.server.repository;

import com.giut.server.entity.UserProfile;
import com.giut.server.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    @Query(
            value = """
                    SELECT profile
                    FROM UserProfile profile
                    WHERE profile.searchable = true
                      AND profile.activityStatus <> :excludedActivityStatus
                      AND EXISTS (
                          SELECT 1
                          FROM User user
                          WHERE user.id = profile.userId
                            AND user.status = :userStatus
                      )
                    """,
            countQuery = """
                    SELECT COUNT(profile)
                    FROM UserProfile profile
                    WHERE profile.searchable = true
                      AND profile.activityStatus <> :excludedActivityStatus
                      AND EXISTS (
                          SELECT 1
                          FROM User user
                          WHERE user.id = profile.userId
                            AND user.status = :userStatus
                      )
                    """
    )
    Page<UserProfile> findPublicProfiles(
            @Param("excludedActivityStatus") UserProfile.ActivityStatus excludedActivityStatus,
            @Param("userStatus") User.Status userStatus,
            Pageable pageable
    );
}
