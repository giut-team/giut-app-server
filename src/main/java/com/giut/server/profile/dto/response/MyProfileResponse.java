package com.giut.server.profile.dto.response;

public record MyProfileResponse(
        boolean profileCompleted,
        boolean universityVerified,
        ProfileResponse profile,
        MyProfileSummaryResponse summary
) {
    public static MyProfileResponse completed(
            boolean universityVerified,
            ProfileResponse profile,
            MyProfileSummaryResponse summary
    ) {
        return new MyProfileResponse(true, universityVerified, profile, summary);
    }

    public static MyProfileResponse notCompleted(boolean universityVerified, MyProfileSummaryResponse summary) {
        return new MyProfileResponse(false, universityVerified, null, summary);
    }
}
