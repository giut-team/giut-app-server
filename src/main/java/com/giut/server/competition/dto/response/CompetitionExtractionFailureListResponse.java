package com.giut.server.competition.dto.response;

import java.util.List;

public record CompetitionExtractionFailureListResponse(
        List<CompetitionExtractionFailureResponse> failures,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
