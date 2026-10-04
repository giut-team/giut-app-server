package com.giut.server.competition.dto.response;

import com.giut.server.competition.entity.Competition;
import io.swagger.v3.oas.annotations.media.Schema;

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
        long viewCount,
        long scrapCount,
        @Schema(description = "이 공모전에 연결된 전체 팀 수", example = "5")
        long teamCount,
        String primaryUrl
) {

    public static PublicCompetitionResponse from(
            Competition competition,
            CompetitionRecruitmentStatus recruitmentStatus,
            String primaryUrl,
            long scrapCount,
            long teamCount
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
                competition.getViewCount(),
                scrapCount,
                teamCount,
                primaryUrl
        );
    }
}
