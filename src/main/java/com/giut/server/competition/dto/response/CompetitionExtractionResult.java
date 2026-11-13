package com.giut.server.competition.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.giut.server.competition.dto.request.CompetitionSubmissionForm;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompetitionExtractionResult(
        Status status,
        String url,
        String sourceUrl,
        CompetitionSubmissionForm draft,
        List<CompetitionSubmissionFieldIssue> requiredInputs,
        List<String> images
) {
    public enum Status { EXISTS, READY, NEEDS_REVIEW, INVALID_ACTIVITY }

    public static CompetitionExtractionResult existing(String url) {
        return new CompetitionExtractionResult(Status.EXISTS, url, null, null, null, null);
    }
}
