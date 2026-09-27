package com.giut.server.dto.competition.response;

public record CompetitionScrapResponse(
        Long competitionId,
        boolean scrapped
) {
}
