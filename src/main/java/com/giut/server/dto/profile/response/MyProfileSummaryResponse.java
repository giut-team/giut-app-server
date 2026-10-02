package com.giut.server.dto.profile.response;

public record MyProfileSummaryResponse(
        long portfolioCount,
        long showcaseCount,
        long myTeamCount,
        long scrapCount,
        long competitionScrapCount,
        long teamScrapCount
) {
}
