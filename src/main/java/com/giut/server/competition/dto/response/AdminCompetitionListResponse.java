package com.giut.server.competition.dto.response;

import java.util.List;

public record AdminCompetitionListResponse(
        List<AdminCompetitionResponse> competitions,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
