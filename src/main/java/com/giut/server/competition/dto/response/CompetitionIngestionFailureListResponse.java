package com.giut.server.competition.dto.response;

import java.util.List;

public record CompetitionIngestionFailureListResponse(
        List<CompetitionIngestionFailureResponse> failures,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
