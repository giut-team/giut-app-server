package com.giut.server.competition.dto.request;

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
        @Schema(description = "영문 enum 사용. 기획/아이디어=PLANNING_IDEA, 홍보/마케팅=ADVERTISING_MARKETING, AI/데이터=AI_DATA, IT/개발=WEB_MOBILE_IT, 과학/공학=SCIENCE_ENGINEERING, 디자인=DESIGN_CHARACTER_WEBTOON, 사진/영상/콘텐츠=VIDEO_UCC_PHOTO, 문학/글쓰기=LITERATURE_WRITING_SCENARIO, 학술/연구=PAPER_REPORT, 문화/예체능=ENTERTAINMENT_ART_MUSIC, 기타=ETC", example = "AI_DATA")
        @NotNull Competition.Category category,
        @Size(max = 150) String hostOrganization,
        @NotBlank @Size(max = 3000) String targetParticipants,
        @Size(max = 10000) String summary,
        @NotNull Instant applicationStartAt,
        @NotNull Instant applicationEndAt,
        @NotEmpty List<@NotNull @Valid CompetitionUrlRequest> urls,
        @Schema(description = "활동 유형. category와 독립적으로 선택합니다.", example = "HACKATHON")
        @NotNull(message = "활동 유형은 필수입니다.") Competition.ActivityType activityType
) {}
