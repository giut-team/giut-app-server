package com.giut.server.competition.dto.response;

import com.giut.server.competition.entity.Competition;
import io.swagger.v3.oas.annotations.media.Schema;

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
        long scrapCount,
        boolean scrapped,
        List<CompetitionUrlResponse> urls,
        @Schema(description = "이 공모전에 연결된 전체 팀 수", example = "5")
        long teamCount,
        @Schema(description = "모집 중인 팀 수", example = "4")
        long recruitingTeamCount,
        @Schema(description = "이 공모전에 연결된 팀 목록")
        List<CompetitionTeamResponse> teams
) {

    public static PublicCompetitionDetailResponse from(
            Competition competition,
            CompetitionRecruitmentStatus recruitmentStatus,
            long scrapCount,
            boolean scrapped,
            List<CompetitionUrlResponse> urls,
            long teamCount,
            long recruitingTeamCount,
            List<CompetitionTeamResponse> teams
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
                scrapCount,
                scrapped,
                urls,
                teamCount,
                recruitingTeamCount,
                teams
        );
    }
}
