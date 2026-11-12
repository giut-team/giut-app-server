package com.giut.server.competition.dto.request;

import com.giut.server.competition.entity.Competition;

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
