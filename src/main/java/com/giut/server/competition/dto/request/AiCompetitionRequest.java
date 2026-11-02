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
        @Schema(description = "대학생만 UNIV_ONLY, 대학생 및 일반인 ALL, 대학생 참여 불가 NONE", example = "ALL")
        @NotNull AiTargetParticipants targetParticipants,
        @Size(max = 10000) String summary,
        @NotNull Instant applicationStartAt,
        @NotNull Instant applicationEndAt,
        @NotEmpty List<@NotNull @Valid CompetitionUrlRequest> urls,
        @Schema(description = "AI 코드 사용: CONTEST=공모전, COMPETITION=경진대회. MULTIPLE_ACTIVITIES는 검토가 필요해 등록되지 않으며 INTERNSHIP은 미지원입니다.", example = "HACKATHON")
        @NotNull(message = "활동 유형은 필수입니다.") AiActivityType activityType,
        @Schema(description = "상금·시상 정보 원문. 없으면 생략합니다.", example = "총상금 1,000만 원")
        @Size(max = 3000) String prizeInfo
) {}
