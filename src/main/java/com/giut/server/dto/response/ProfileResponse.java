package com.giut.server.dto.response;

import com.giut.server.entity.UserProfile;

public record ProfileResponse(
        Long userId,
        Long departmentId,
        String departmentName,
        Short grade,
        UserProfile.Gender gender,
        String profileImageUrl,
        String bio,
        boolean searchable
) {
    public static ProfileResponse from(UserProfile profile) {
        return new ProfileResponse(
                profile.getUserId(),
                profile.getDepartment().getId().longValue(),
                profile.getDepartment().getName(),
                profile.getGrade(),
                profile.getGender(),
                profile.getProfileImageUrl(),
                profile.getBio(),
                profile.isSearchable()
        );
    }
}
