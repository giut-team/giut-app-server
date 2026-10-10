package com.giut.server.profile.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record MyProfileSummaryResponse(
        long portfolioCount,
        long showcaseCount,
        long myTeamCount,
        long scrapCount,
        long competitionScrapCount,
        long teamScrapCount,
        @Schema(description = "현재 수락 또는 거절을 기다리는 팀 합류 제안 수", example = "2")
        long receivedProposalCount,
        @Schema(description = "다른 사용자에게 현재 받은 프로필 추천 수. 추천 취소 시 감소합니다.", example = "7")
        long receivedRecommendationCount,
        @Schema(description = "참여 기록이 있는 서로 다른 팀의 누적 개수. 팀장으로 생성한 팀과 탈퇴·강퇴 기록도 포함합니다.", example = "4")
        long collaborationCount
) {
}
