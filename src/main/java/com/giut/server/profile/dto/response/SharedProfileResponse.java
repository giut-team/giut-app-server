package com.giut.server.profile.dto.response;

import com.giut.server.profile.dto.common.ProfileCodeNameResponse;
import com.giut.server.profile.entity.UserProfile;

import java.util.List;

/** 비회원 공유 화면에 허용된 프로필 필드만 포함한다. */
public record SharedProfileResponse(
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
    public static SharedProfileResponse from(PublicProfileResponse profile) {
        return new SharedProfileResponse(
                profile.nickname(),
                profile.universityVerified(),
                profile.profileImageUrl(),
                profile.activityStatus(),
                profile.activityStatusName(),
                profile.primaryRoles(),
                profile.departmentName(),
                profile.grade(),
                profile.bio(),
                profile.skills()
        );
    }
}
