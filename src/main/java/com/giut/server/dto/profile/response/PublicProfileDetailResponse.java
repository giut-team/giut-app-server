package com.giut.server.dto.profile.response;

/** 공개 프로필 상세 화면에서 사용하는 응답이다. */
public record PublicProfileDetailResponse(
        String nickname,
        boolean universityVerified,
        ProfileResponse profile
) {
}
