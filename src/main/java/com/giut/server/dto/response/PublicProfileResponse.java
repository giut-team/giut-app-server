package com.giut.server.dto.response;

import com.giut.server.entity.UserProfile;

import java.util.List;

public record PublicProfileResponse(
        Long userId,
        String nickname,
        boolean universityVerified,
        UserProfile.DepartmentType department,
        String departmentName,
        Short grade,
        String profileImageUrl,
        UserProfile.ActivityStatus activityStatus,
        String activityStatusName,
        String bio,
        List<ProfilePrimaryRoleResponse> primaryRoles,
        List<ProfileRoleResponse> roles,
        List<ProfileTagSummaryResponse> tags
) {
}
