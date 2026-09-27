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
                    SELECT profile.*
                    FROM user_profiles profile
                    JOIN users user ON user.id = profile.user_id
                    WHERE profile.is_searchable = true
                      AND profile.activity_status <> :excludedActivityStatus
                      AND user.status = :userStatus
                      AND profile.user_id <> :excludedUserId
                      AND (:primaryRole IS NULL OR JSON_CONTAINS(profile.primary_roles, JSON_QUOTE(:primaryRole)) = 1)
                      AND (
                          :roleCode IS NULL
                          OR EXISTS (
                              SELECT 1
                              FROM user_profile_roles userProfileRole
                              JOIN profile_roles role ON role.id = userProfileRole.role_id
                              WHERE userProfileRole.user_id = profile.user_id
                                AND role.code = :roleCode
                          )
                      )
                      AND (:activityStatus IS NULL OR profile.activity_status = :activityStatus)
                      AND (:departmentType IS NULL OR profile.department_type = :departmentType)
                      AND (
                          :skillTagId IS NULL
                          OR EXISTS (
                              SELECT 1
                              FROM user_profile_tags userProfileTag
                              JOIN profile_tags tag ON tag.id = userProfileTag.tag_id
                              WHERE userProfileTag.user_id = profile.user_id
                                AND tag.id = :skillTagId
                                AND tag.tag_type = 'SKILL'
                          )
                      )
                    ORDER BY profile.user_id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM user_profiles profile
                    JOIN users user ON user.id = profile.user_id
                    WHERE profile.is_searchable = true
                      AND profile.activity_status <> :excludedActivityStatus
                      AND user.status = :userStatus
                      AND profile.user_id <> :excludedUserId
                      AND (:primaryRole IS NULL OR JSON_CONTAINS(profile.primary_roles, JSON_QUOTE(:primaryRole)) = 1)
                      AND (
                          :roleCode IS NULL
                          OR EXISTS (
                              SELECT 1
                              FROM user_profile_roles userProfileRole
                              JOIN profile_roles role ON role.id = userProfileRole.role_id
                              WHERE userProfileRole.user_id = profile.user_id
                                AND role.code = :roleCode
                          )
                      )
                      AND (:activityStatus IS NULL OR profile.activity_status = :activityStatus)
                      AND (:departmentType IS NULL OR profile.department_type = :departmentType)
                      AND (
                          :skillTagId IS NULL
                          OR EXISTS (
                              SELECT 1
                              FROM user_profile_tags userProfileTag
                              JOIN profile_tags tag ON tag.id = userProfileTag.tag_id
                              WHERE userProfileTag.user_id = profile.user_id
                                AND tag.id = :skillTagId
                                AND tag.tag_type = 'SKILL'
                          )
                      )
                    """,
            nativeQuery = true
    )
    Page<UserProfile> findPublicProfiles(
            @Param("excludedActivityStatus") String excludedActivityStatus,
            @Param("userStatus") String userStatus,
            @Param("excludedUserId") Long excludedUserId,
            @Param("primaryRole") String primaryRole,
            @Param("roleCode") String roleCode,
            @Param("activityStatus") String activityStatus,
            @Param("departmentType") String departmentType,
            @Param("skillTagId") Long skillTagId,
            Pageable pageable
    );
}
