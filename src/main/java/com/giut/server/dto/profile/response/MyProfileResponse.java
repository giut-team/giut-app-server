package com.giut.server.dto.profile.response;

public record MyProfileResponse(
        boolean profileCompleted,
        ProfileResponse profile,
        MyProfileSummaryResponse summary
) {
    public static MyProfileResponse completed(ProfileResponse profile, MyProfileSummaryResponse summary) {
        return new MyProfileResponse(true, profile, summary);
    }

    public static MyProfileResponse notCompleted(MyProfileSummaryResponse summary) {
        return new MyProfileResponse(false, null, summary);
    }
}
