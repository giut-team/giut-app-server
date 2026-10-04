package com.giut.server.competition.dto.response;

public record CompetitionScrapResponse(
        Long competitionId,
        boolean scrapped
) {
}
