package com.giut.server.profile.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 공개 프로필 상세 화면에서 사용하는 응답이다. */
public record PublicProfileDetailResponse(
        String nickname,
        boolean universityVerified,
        @Schema(description = "현재 수락 또는 거절을 기다리는 팀 합류 제안 수", example = "2")
        long receivedProposalCount,
        @Schema(description = "현재 로그인 사용자가 이 프로필을 추천했는지 여부", example = "true")
        boolean recommended,
        @Schema(description = "이 사용자가 현재 받은 총 추천 수. 추천 취소 시 감소합니다.", example = "5")
        long recommendationCount,
        ProfileResponse profile
) {
}
