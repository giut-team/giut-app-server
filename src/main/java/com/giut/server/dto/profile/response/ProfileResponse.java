package com.giut.server.dto.profile.response;

import com.giut.server.entity.UserProfile;
import com.giut.server.dto.profile.common.ActivityHistoryDto;
import com.giut.server.dto.profile.common.PortfolioItemDto;
import com.giut.server.dto.profile.common.ProfileCodeNameResponse;

import java.util.List;

public record ProfileResponse(
        Long userId,
        String nickname,
        String departmentName,
        Short grade,
        List<ProfileCodeNameResponse> primaryRoles,
        String activityStatusName,
        String profileImageUrl,
        String bio,
        boolean searchable,
        List<ProfileCodeNameResponse> roles,
        List<ProfileTagResponse> tags,
        List<PortfolioItemDto> portfolioItems,
        List<ActivityHistoryDto> activityHistories
) {
    public static ProfileResponse from(
            UserProfile profile,
            List<ProfileCodeNameResponse> primaryRoles,
            List<ProfileCodeNameResponse> roles,
            List<ProfileTagResponse> tags,
            List<PortfolioItemDto> portfolioItems,
            List<ActivityHistoryDto> activityHistories
    ) {
        return new ProfileResponse(
                profile.getUserId(),
                profile.getUser().getNickname(),
                profile.getDepartment().getDisplayName(),
                profile.getGrade(),
                primaryRoles,
                profile.getActivityStatus().getDisplayName(),
                profile.getProfileImageUrl(),
                profile.getBio(),
                profile.isSearchable(),
                roles,
                tags,
                portfolioItems,
                activityHistories
        );
    }
}
