package com.giut.server.competition.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.giut.server.competition.dto.request.CompetitionSubmissionForm;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompetitionExtractionResult(
        Status status,
        String url,
        Long competitionId,
        String sourceUrl,
        CompetitionSubmissionForm draft,
        List<CompetitionSubmissionFieldIssue> requiredInputs,
        List<String> images
) {
    public enum Status { EXISTS, READY, NEEDS_REVIEW, INVALID_ACTIVITY }

    /**
     * 이미 등록·제출된 공모전. {@code competitionId}는 공개 중(PUBLISHED)인 공모전일 때만 채운다.
     * 검토 대기·반려·보관 중인 공모전은 존재만 알리고 ID를 노출하지 않는다.
     */
    public static CompetitionExtractionResult existing(String url, Long competitionId) {
        return new CompetitionExtractionResult(Status.EXISTS, url, competitionId, null, null, null, null);
    }
}
