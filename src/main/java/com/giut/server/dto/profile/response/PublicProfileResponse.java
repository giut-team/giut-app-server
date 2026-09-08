package com.giut.server.dto.profile.response;

import com.giut.server.entity.UserProfile;
import com.giut.server.dto.profile.common.ProfileCodeNameResponse;

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
