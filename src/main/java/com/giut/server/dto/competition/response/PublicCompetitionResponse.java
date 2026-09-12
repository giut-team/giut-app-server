package com.giut.server.dto.competition.response;

import com.giut.server.entity.Competition;

import java.time.Instant;

public record PublicCompetitionResponse(
        Long id,
        String title,
        Competition.Category category,
        String categoryName,
        String hostOrganization,
        String summary,
        Instant applicationStartAt,
        Instant applicationEndAt,
        CompetitionRecruitmentStatus recruitmentStatus,
        String recruitmentStatusName,
        String primaryUrl
) {

    public static PublicCompetitionResponse from(
            Competition competition,
            CompetitionRecruitmentStatus recruitmentStatus,
            String primaryUrl
    ) {
        return new PublicCompetitionResponse(
                competition.getId(),
                competition.getTitle(),
                competition.getCategory(),
                competition.getCategory().getDisplayName(),
                competition.getHostOrganization(),
                competition.getSummary(),
                competition.getApplicationStartAt(),
                competition.getApplicationEndAt(),
                recruitmentStatus,
                recruitmentStatus.getDisplayName(),
                primaryUrl
        );
    }
}
