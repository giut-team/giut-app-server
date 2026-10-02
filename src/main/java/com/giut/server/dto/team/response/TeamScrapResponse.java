package com.giut.server.dto.team.response;

public record TeamScrapResponse(
        Long teamId,
        boolean scrapped
) {
}
