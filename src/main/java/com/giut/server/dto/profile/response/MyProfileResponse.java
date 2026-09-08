package com.giut.server.dto.profile.response;

public record MyProfileResponse(
        boolean profileCompleted,
        ProfileResponse profile
) {
    public static MyProfileResponse completed(ProfileResponse profile) {
        return new MyProfileResponse(true, profile);
    }

    public static MyProfileResponse notCompleted() {
        return new MyProfileResponse(false, null);
    }
}
