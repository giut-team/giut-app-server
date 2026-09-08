package com.giut.server.dto.response;

import com.giut.server.entity.UserProfile;

import java.util.List;

public record PublicProfileResponse(
        Long userId,
        String nickname,
        boolean universityVerified,
        String profileImageUrl,
        UserProfile.ActivityStatus activityStatus,
        String activityStatusName,
        List<ProfilePrimaryRoleResponse> primaryRoles,
        String departmentName,
        Short grade,
        String bio,
        List<ProfileTagSummaryResponse> skills
) {
}
