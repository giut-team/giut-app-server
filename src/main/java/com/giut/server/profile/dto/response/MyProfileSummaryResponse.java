package com.giut.server.profile.dto.response;

public record MyProfileSummaryResponse(
        long portfolioCount,
        long showcaseCount,
        long myTeamCount,
        long scrapCount,
        long competitionScrapCount,
        long teamScrapCount
) {
}
