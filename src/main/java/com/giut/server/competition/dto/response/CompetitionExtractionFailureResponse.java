package com.giut.server.competition.dto.response;

import com.giut.server.competition.entity.CompetitionExtractionJob;

import java.time.Instant;

/** 관리자용 추출 최종 실패 한 건. errorDetail은 관리자만 보는 원인 요약이다. */
public record CompetitionExtractionFailureResponse(
        Long jobId,
        String sourceUrl,
        String errorCode,
        String errorMessage,
        String errorDetail,
        int attemptCount,
        long waitingUsers,
        Instant requestedAt,
        Instant failedAt,
        Instant expiresAt
) {
    public static CompetitionExtractionFailureResponse from(CompetitionExtractionJob job, long waitingUsers) {
        return new CompetitionExtractionFailureResponse(job.getId(), job.getSourceUrl(), job.getErrorCode(),
                job.getErrorMessage(), job.getErrorDetail(), job.getAttemptCount(), waitingUsers, job.getCreatedAt(),
                job.getCompletedAt(), job.getExpiresAt());
    }
}
