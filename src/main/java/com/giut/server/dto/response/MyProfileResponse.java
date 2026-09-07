package com.giut.server.dto.response;

import com.giut.server.entity.UserProfile;
import lombok.AllArgsConstructor;
import lombok.Getter;

public record MyProfileResponse(
        boolean profileCompleted,
        ProfileResponse profile
) {
    public static MyProfileResponse from(UserProfile userProfile) {
        return new MyProfileResponse(
                true,
                ProfileResponse.from(userProfile)
        );
    }

    public static MyProfileResponse notCompleted() {
        return new MyProfileResponse(false, null);
    }
}
