package com.giut.server.competition.dto.request;

import com.giut.server.competition.entity.Competition;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record CompetitionSubmissionForm(
        String title,
        Competition.Category category,
        String hostOrganization,
        String targetParticipants,
        String summary,
        Instant applicationStartAt,
        Instant applicationEndAt,
        @Schema(description = "공고 링크 목록. 저장되는 링크는 원본 링크를 포함해 최대 10개입니다. "
                + "제출한 원본 링크(sourceUrl)가 목록에 없으면 SOURCE_ORIGINAL로 자동 추가되므로, 이때는 9개까지 입력할 수 있습니다.")
        List<CompetitionUrlRequest> urls,
        Competition.ActivityType activityType,
        String prizeInfo,
        Long totalPrizeAmount,
        Boolean isForEnrolledOnly,
        Boolean isTeam
) {
    public CompetitionSubmissionForm withUrls(List<CompetitionUrlRequest> urls) {
        return new CompetitionSubmissionForm(title, category, hostOrganization, targetParticipants,
                summary, applicationStartAt, applicationEndAt, urls, activityType, prizeInfo, totalPrizeAmount,
                isForEnrolledOnly, isTeam);
    }

    public CreateCompetitionRequest toRegistrationRequest() {
        return new CreateCompetitionRequest(title, category, hostOrganization, targetParticipants,
                summary, applicationStartAt, applicationEndAt, Competition.PublicationStatus.DRAFT,
                urls, activityType, prizeInfo, totalPrizeAmount);
    }
}
