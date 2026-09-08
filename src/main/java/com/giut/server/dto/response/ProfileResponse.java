package com.giut.server.dto.response;

import com.giut.server.entity.UserProfile;

import java.util.List;

public record ProfileResponse(
        Long userId,
        UserProfile.DepartmentType department,
        String departmentName,
        Short grade,
        UserProfile.Gender gender,
        List<ProfilePrimaryRoleResponse> primaryRoles,
        UserProfile.ActivityStatus activityStatus,
        String activityStatusName,
        String profileImageUrl,
        String bio,
        boolean searchable,
        List<ProfileRoleResponse> roles,
        List<ProfileTagResponse> tags,
        List<ProfileLinkResponse> links
) {
    public static ProfileResponse from(
            UserProfile profile,
            List<ProfilePrimaryRoleResponse> primaryRoles,
            List<ProfileRoleResponse> roles,
            List<ProfileTagResponse> tags,
            List<ProfileLinkResponse> links
    ) {
        return new ProfileResponse(
                profile.getUserId(),
                profile.getDepartment(),
                profile.getDepartment().getDisplayName(),
                profile.getGrade(),
                profile.getGender(),
                primaryRoles,
                profile.getActivityStatus(),
                profile.getActivityStatus().getDisplayName(),
                profile.getProfileImageUrl(),
                profile.getBio(),
                profile.isSearchable(),
                roles,
                tags,
                links
        );
    }
}
