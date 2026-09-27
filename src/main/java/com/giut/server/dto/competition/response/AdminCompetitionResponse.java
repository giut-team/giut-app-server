package com.giut.server.dto.competition.response;

import com.giut.server.entity.Competition;

import java.time.Instant;
import java.util.List;

public record AdminCompetitionResponse(
        Long id,
        String title,
        Competition.Category category,
        String hostOrganization,
        String targetParticipants,
        String summary,
        Instant applicationStartAt,
        Instant applicationEndAt,
        Competition.PublicationStatus publicationStatus,
        Competition.VerificationStatus verificationStatus,
        Instant verifiedAt,
        List<CompetitionUrlResponse> urls
) {

    public static AdminCompetitionResponse from(Competition competition, List<CompetitionUrlResponse> urls) {
        return new AdminCompetitionResponse(
                competition.getId(),
                competition.getTitle(),
                competition.getCategory(),
                competition.getHostOrganization(),
                competition.getTargetParticipants(),
                competition.getSummary(),
                competition.getApplicationStartAt(),
                competition.getApplicationEndAt(),
                competition.getPublicationStatus(),
                competition.getVerificationStatus(),
                competition.getVerifiedAt(),
                urls
        );
    }
}
