package com.giut.server.competition.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import tools.jackson.databind.annotation.JsonDeserialize;
import com.giut.server.competition.entity.Competition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record AiCompetitionRequest(
        @NotBlank @Size(max = 200) String title,
        @Schema(description = "AI 카테고리: IDEA, MARKETING, AI_DATA, IT_DEVELOP, SCIENCE_ENGINEERING, DESIGN, PHOTO_CONTENTS, LITERATURE, RESEARCH, CULTURE_ARTS, ETC. 기존 기웃 코드도 허용합니다.", example = "IT_DEVELOP", allowableValues = {"IDEA", "MARKETING", "AI_DATA", "IT_DEVELOP", "SCIENCE_ENGINEERING", "DESIGN", "PHOTO_CONTENTS", "LITERATURE", "RESEARCH", "CULTURE_ARTS", "ETC", "PLANNING_IDEA", "ADVERTISING_MARKETING", "WEB_MOBILE_IT", "DESIGN_CHARACTER_WEBTOON", "VIDEO_UCC_PHOTO", "LITERATURE_WRITING_SCENARIO", "PAPER_REPORT", "ENTERTAINMENT_ART_MUSIC", "GAME_SOFTWARE", "ARCHITECTURE_CONSTRUCTION_INTERIOR", "NAMING_SLOGAN"})
        @JsonDeserialize(using = AiCompetitionCategoryDeserializer.class)
        @NotNull Competition.Category category,
        @Size(max = 150) String hostOrganization,
        @Schema(description = "대학생만 UNIV_ONLY, 대학생 및 일반인 ALL, 대학생 참여 불가 NONE", example = "ALL")
        @NotNull AiTargetParticipants targetParticipants,
        @Size(max = 10000) String summary,
        @NotNull Instant applicationStartAt,
        @NotNull Instant applicationEndAt,
        @NotEmpty List<@NotNull @Valid CompetitionUrlRequest> urls,
        @Schema(description = "AI 코드 사용: CONTEST=공모전, COMPETITION=경진대회. MULTIPLE_ACTIVITIES는 검토가 필요해 등록되지 않으며 INTERNSHIP은 미지원입니다.", example = "HACKATHON")
        @NotNull(message = "활동 유형은 필수입니다.") AiActivityType activityType,
        @Schema(description = "상금·시상 정보 원문. 생략, null 또는 공백이면 상금 미정으로 저장합니다.", example = "총상금 1,000만 원")
        @Size(max = 3000) String prizeInfo,
        @Schema(description = "총상금 숫자. 없으면 null 또는 생략합니다.", example = "10000000")
        @JsonAlias("total_prize_amount") Long totalPrizeAmount
) {
    public AiCompetitionRequest {
        if (prizeInfo == null || prizeInfo.isBlank()) {
            prizeInfo = "상금 미정";
        }
    }
}
