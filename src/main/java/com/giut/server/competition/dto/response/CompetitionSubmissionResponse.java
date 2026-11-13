package com.giut.server.competition.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.giut.server.competition.entity.Competition;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompetitionSubmissionResponse(
        Status status,
        String url,
        Long competitionId,
        Competition.PublicationStatus publicationStatus,
        Competition.VerificationStatus verificationStatus,
        List<CompetitionSubmissionFieldIssue> requiredInputs
) {
    public enum Status { EXISTS, SUBMITTED, INVALID_INPUT }

    /** {@code competitionId}는 이미 있는 공모전이 공개 중(PUBLISHED)일 때만 채운다. */
    public static CompetitionSubmissionResponse existing(String url, Long competitionId) {
        return new CompetitionSubmissionResponse(Status.EXISTS, url, competitionId, null, null, null);
    }

    public static CompetitionSubmissionResponse submitted(Long competitionId) {
        return new CompetitionSubmissionResponse(Status.SUBMITTED, null, competitionId,
                Competition.PublicationStatus.DRAFT, Competition.VerificationStatus.MANUAL_REVIEW_PENDING, null);
    }

    public static CompetitionSubmissionResponse invalid(List<CompetitionSubmissionFieldIssue> requiredInputs) {
        return new CompetitionSubmissionResponse(Status.INVALID_INPUT, null, null, null, null, requiredInputs);
    }
}
