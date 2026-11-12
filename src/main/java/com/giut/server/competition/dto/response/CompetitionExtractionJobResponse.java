package com.giut.server.competition.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.giut.server.competition.entity.CompetitionExtractionJob;

import java.time.Instant;

/**
 * 회원 URL 추출 작업 접수·조회 응답.
 * EXISTS는 기존 extract-preview와 같은 의미(이미 등록·제출된 링크, AI 호출 없음)이며 jobId가 없다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompetitionExtractionJobResponse(
        Long jobId,
        Status status,
        String url,
        String sourceUrl,
        Integer attemptCount,
        Instant createdAt,
        Instant completedAt,
        CompetitionExtractPreviewResponse result,
        Error error
) {
    public enum Status { EXISTS, PENDING, RUNNING, SUCCEEDED, FAILED }

    public record Error(String code, String message) {
    }

    public static CompetitionExtractionJobResponse existing(String url) {
        return new CompetitionExtractionJobResponse(null, Status.EXISTS, url, null, null, null, null, null, null);
    }

    public static CompetitionExtractionJobResponse of(CompetitionExtractionJob job, CompetitionExtractPreviewResponse result) {
        Error error = job.getStatus() == CompetitionExtractionJob.Status.FAILED
                ? new Error(job.getErrorCode(), job.getErrorMessage()) : null;
        return new CompetitionExtractionJobResponse(job.getId(), Status.valueOf(job.getStatus().name()), null,
                job.getSourceUrl(), job.getAttemptCount(), job.getCreatedAt(), job.getCompletedAt(), result, error);
    }

    public boolean inProgress() {
        return status == Status.PENDING || status == Status.RUNNING;
    }
}
