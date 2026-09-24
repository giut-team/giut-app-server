package com.giut.server.dto.competition.response;

import com.giut.server.entity.CompetitionUrl;

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
