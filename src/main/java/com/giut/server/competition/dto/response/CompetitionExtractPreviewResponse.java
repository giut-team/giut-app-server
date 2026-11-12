package com.giut.server.competition.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.giut.server.competition.dto.request.CompetitionSubmissionForm;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompetitionExtractPreviewResponse(
        Status status,
        String url,
        String sourceUrl,
        CompetitionSubmissionForm draft,
        List<CompetitionSubmissionFieldIssue> requiredInputs,
        List<String> images
) {
    public enum Status { EXISTS, READY, NEEDS_REVIEW, INVALID_ACTIVITY }

    public static CompetitionExtractPreviewResponse existing(String url) {
        return new CompetitionExtractPreviewResponse(Status.EXISTS, url, null, null, null, null);
    }
}
