package com.giut.server.profile.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 공개 프로필 상세 화면에서 사용하는 응답이다. */
public record PublicProfileDetailResponse(
        String nickname,
        boolean universityVerified,
        @Schema(description = "현재 수락 또는 거절을 기다리는 팀 합류 제안 수", example = "2")
        long receivedProposalCount,
        ProfileResponse profile
) {
}
