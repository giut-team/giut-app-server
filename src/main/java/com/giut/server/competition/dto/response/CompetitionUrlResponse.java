package com.giut.server.competition.dto.response;

import com.giut.server.competition.entity.CompetitionUrl;

public record CompetitionUrlResponse(
        Long id,
        CompetitionUrl.Type type,
        String url,
        boolean primary
) {

    public static CompetitionUrlResponse from(CompetitionUrl competitionUrl) {
        return new CompetitionUrlResponse(
                competitionUrl.getId(),
                competitionUrl.getUrlType(),
                competitionUrl.getUrl(),
                competitionUrl.isPrimary()
        );
    }
}
