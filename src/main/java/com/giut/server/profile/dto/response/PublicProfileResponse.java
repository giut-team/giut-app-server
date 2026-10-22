package com.giut.server.profile.dto.response;

import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.dto.common.ProfileCodeNameResponse;

import java.util.List;

public record PublicProfileResponse(
        Long userId,
        String nickname,
        boolean universityVerified,
        String profileImageUrl,
        UserProfile.ActivityStatus activityStatus,
        String activityStatusName,
        List<ProfileCodeNameResponse> primaryRoles,
        String departmentName,
        Short grade,
        String bio,
        List<ProfileTagSummaryResponse> skills
) {
}
