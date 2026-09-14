package com.giut.server.dto.competition.response;

import com.giut.server.entity.Competition;

import java.time.Instant;
import java.util.List;

public record PublicCompetitionDetailResponse(
        Long id,
        String title,
        Competition.Category category,
        String categoryName,
        String hostOrganization,
        String targetParticipants,
        String summary,
        Instant applicationStartAt,
        Instant applicationEndAt,
        CompetitionRecruitmentStatus recruitmentStatus,
        String recruitmentStatusName,
        long viewCount,
        boolean scrapped,
        List<CompetitionUrlResponse> urls
) {

    public static PublicCompetitionDetailResponse from(
            Competition competition,
            CompetitionRecruitmentStatus recruitmentStatus,
            boolean scrapped,
            List<CompetitionUrlResponse> urls
    ) {
        return new PublicCompetitionDetailResponse(
                competition.getId(),
                competition.getTitle(),
                competition.getCategory(),
                competition.getCategory().getDisplayName(),
                competition.getHostOrganization(),
                competition.getTargetParticipants(),
                competition.getSummary(),
                competition.getApplicationStartAt(),
                competition.getApplicationEndAt(),
                recruitmentStatus,
                recruitmentStatus.getDisplayName(),
                competition.getViewCount(),
                scrapped,
                urls
        );
    }
}
