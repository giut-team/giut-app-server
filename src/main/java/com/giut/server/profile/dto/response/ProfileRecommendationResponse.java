package com.giut.server.profile.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProfileRecommendationResponse(
        @Schema(description = "추천 대상 사용자 ID", example = "12") Long userId,
        @Schema(description = "현재 로그인 사용자의 추천 여부", example = "true") boolean recommended,
        @Schema(description = "이 사용자가 현재 받은 추천 수. 추천 취소 시 감소합니다.", example = "5")
        long recommendationCount
) {}
