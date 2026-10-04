package com.giut.server.team.dto.response;

public record TeamScrapResponse(
        Long teamId,
        boolean scrapped
) {
}
