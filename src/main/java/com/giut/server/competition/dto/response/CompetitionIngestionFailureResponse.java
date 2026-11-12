package com.giut.server.competition.dto.response;

import com.giut.server.competition.entity.CompetitionIngestionFailure;

import java.time.Instant;

public record CompetitionIngestionFailureResponse(
        Long id,
        String batchId,
        int itemIndex,
        CompetitionIngestionFailure.FailureType failureType,
        String errorMessage,
        String title,
        String primaryUrl,
        String rawPayload,
        Instant receivedAt
) {
    public static CompetitionIngestionFailureResponse from(CompetitionIngestionFailure failure) {
        return new CompetitionIngestionFailureResponse(failure.getId(), failure.getBatchId(), failure.getItemIndex(),
                failure.getFailureType(), failure.getErrorMessage(), failure.getTitle(), failure.getPrimaryUrl(),
                failure.getRawPayload(), failure.getReceivedAt());
    }
}
