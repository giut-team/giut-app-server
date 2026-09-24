package com.giut.server.dto.competition.response;

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
