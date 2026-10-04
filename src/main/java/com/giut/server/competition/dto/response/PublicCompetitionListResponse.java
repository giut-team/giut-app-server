package com.giut.server.competition.dto.response;

import java.util.List;

public record PublicCompetitionListResponse(
        List<PublicCompetitionResponse> competitions,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
