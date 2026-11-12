package com.giut.server.competition.dto.response;

import com.giut.server.competition.entity.Competition;

import java.time.Instant;
import java.util.List;

public record AdminCompetitionResponse(
        Long id,
        String title,
        Competition.ActivityType activityType,
        String activityTypeName,
        Competition.Category category,
        String hostOrganization,
        String targetParticipants,
        String summary,
        String prizeInfo,
        Long totalPrizeAmount,
        Instant applicationStartAt,
        Instant applicationEndAt,
        Competition.PublicationStatus publicationStatus,
        Competition.VerificationStatus verificationStatus,
        Instant verifiedAt,
        CompetitionRegistrationSource registrationSource,
        List<CompetitionUrlResponse> urls,
        Boolean isForEnrolledOnly,
        Boolean isTeam
) {

    public static AdminCompetitionResponse from(Competition competition, CompetitionRegistrationSource registrationSource,
                                                List<CompetitionUrlResponse> urls) {
        return new AdminCompetitionResponse(
                competition.getId(),
                competition.getTitle(),
                competition.getActivityType(),
                competition.getActivityType() == null ? null : competition.getActivityType().getDisplayName(),
                competition.getCategory(),
                competition.getHostOrganization(),
                competition.getTargetParticipants(),
                competition.getSummary(),
                competition.getPrizeInfo(),
                competition.getTotalPrizeAmount(),
                competition.getApplicationStartAt(),
                competition.getApplicationEndAt(),
                competition.getPublicationStatus(),
                competition.getVerificationStatus(),
                competition.getVerifiedAt(),
                registrationSource,
                urls,
                competition.getIsForEnrolledOnly(),
                competition.getIsTeam()
        );
    }
}
