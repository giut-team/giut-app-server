package com.giut.server.dto.profile.response;

import com.giut.server.entity.UserProfile;
import com.giut.server.dto.profile.common.PortfolioItemDto;
import com.giut.server.dto.profile.common.ProfileCodeNameResponse;
import com.giut.server.dto.profile.common.ProfileLinkDto;

import java.util.List;

public record ProfileResponse(
        Long userId,
        UserProfile.DepartmentType department,
        String departmentName,
        Short grade,
        UserProfile.Gender gender,
        List<ProfileCodeNameResponse> primaryRoles,
        UserProfile.ActivityStatus activityStatus,
        String activityStatusName,
        String profileImageUrl,
        String bio,
        boolean searchable,
        List<ProfileCodeNameResponse> roles,
        List<ProfileTagResponse> tags,
        List<ProfileLinkDto> links,
        List<PortfolioItemDto> portfolioItems
) {
    public static ProfileResponse from(
            UserProfile profile,
            List<ProfileCodeNameResponse> primaryRoles,
            List<ProfileCodeNameResponse> roles,
            List<ProfileTagResponse> tags,
            List<ProfileLinkDto> links,
            List<PortfolioItemDto> portfolioItems
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
                links,
                portfolioItems
        );
    }
}
