package com.giut.server.profile.repository;

import com.giut.server.profile.entity.UserProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserProfile p where p.userId = :userId")
    Optional<UserProfile> findByIdForUpdate(@Param("userId") Long userId);

    // 네이티브 쿼리인 이유: 조건마다 "파라미터가 null이면 무시"하는 선택적 필터가 많고 JSON_CONTAINS도 써야 해서,
    // Specification보다 SQL로 직접 쓰는 게 더 명확하다. count 쿼리를 별도로 둔 것도 본문과 동일한 조건을
    // 페이징 총 개수 계산에 맞춰 함께 유지하기 위함이다.
    @Query(
            value = """
                    SELECT profile.*
                    FROM user_profiles profile
                    JOIN users profileUser ON profileUser.id = profile.user_id
                    WHERE profile.is_searchable = true
                      AND profile.activity_status <> :excludedActivityStatus
                      AND profileUser.status = :userStatus
                      AND profile.user_id <> :excludedUserId
                      -- 나(excludedUserId)와 상대방 사이에 차단 관계가 어느 방향이든 있으면 제외
                      AND NOT EXISTS (
                          SELECT 1 FROM user_blocks blockedRelation
                          WHERE (blockedRelation.blocker_user_id = :excludedUserId AND blockedRelation.blocked_user_id = profile.user_id)
                             OR (blockedRelation.blocker_user_id = profile.user_id AND blockedRelation.blocked_user_id = :excludedUserId)
                      )
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
                      AND (:grade IS NULL OR profile.grade = :grade)
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
                    JOIN users profileUser ON profileUser.id = profile.user_id
                    WHERE profile.is_searchable = true
                      AND profile.activity_status <> :excludedActivityStatus
                      AND profileUser.status = :userStatus
                      AND profile.user_id <> :excludedUserId
                      AND NOT EXISTS (
                          SELECT 1 FROM user_blocks blockedRelation
                          WHERE (blockedRelation.blocker_user_id = :excludedUserId AND blockedRelation.blocked_user_id = profile.user_id)
                             OR (blockedRelation.blocker_user_id = profile.user_id AND blockedRelation.blocked_user_id = :excludedUserId)
                      )
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
                      AND (:grade IS NULL OR profile.grade = :grade)
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
            @Param("grade") Short grade,
            @Param("skillTagId") Long skillTagId,
            Pageable pageable
    );
}
