package com.giut.server.competition.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.util.stream.IntStream;

public record AiCompetitionRequest(
        @NotBlank @Size(max = 200) String title,
        @Schema(description = "AI 카테고리 배열: IDEA, MARKETING, AI_DATA, IT_DEVELOP, SCIENCE_ENGINEERING, DESIGN, PHOTO_CONTENTS, LITERATURE, RESEARCH, CULTURE_ARTS, ETC. 기존 기웃 코드도 허용합니다. 첫 번째 값을 대표 카테고리로 저장합니다.", example = "[\"IT_DEVELOP\"]")
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        @JsonDeserialize(contentUsing = AiCompetitionCategoryDeserializer.class)
        @NotEmpty(message = "카테고리는 하나 이상이어야 합니다.") List<Competition.@NotNull Category> category,
        @Size(max = 150) String hostOrganization,
        @Schema(description = "대학생만 UNIV_ONLY, 대학생 및 일반인 ALL, 대학생 참여 불가 NONE", example = "ALL")
        @NotNull AiTargetParticipants targetParticipants,
        @Size(max = 10000) String summary,
        @Schema(description = "모집 시작일. null 또는 생략 가능. 전체 ISO-8601 일시 또는 날짜만(yyyy-MM-dd, Asia/Seoul 자정으로 저장)", example = "2026-11-10", nullable = true)
        @JsonDeserialize(using = AiFlexibleInstantDeserializer.Start.class)
        Instant applicationStartAt,
        @Schema(description = "모집 마감일. 전체 ISO-8601 일시 또는 날짜만(yyyy-MM-dd, Asia/Seoul 23:59:59로 저장)", example = "2026-12-10")
        @JsonDeserialize(using = AiFlexibleInstantDeserializer.End.class)
        @NotNull Instant applicationEndAt,
        @Schema(description = "공모전 URL 목록. primary를 생략하면 첫 번째 URL을 대표로 지정합니다.")
        @NotEmpty List<@NotNull @Valid CompetitionUrlRequest> urls,
        @Schema(description = "AI 코드 사용: CONTEST=공모전, COMPETITION=경진대회. INVALID_ACTIVITY는 부적합 활동으로 등록되지 않으며 INTERNSHIP은 미지원입니다.", example = "HACKATHON")
        @JsonProperty("activityKind") @JsonAlias("activityType")
        @NotNull(message = "활동 유형은 필수입니다.") AiActivityType activityType,
        @Schema(description = "상금·시상 정보 원문. 생략, null 또는 공백이면 상금 미정으로 저장합니다.", example = "총상금 1,000만 원")
        @JsonAlias("prize_info") @Size(max = 3000) String prizeInfo,
        @Schema(description = "총상금 숫자. 없으면 null 또는 생략합니다.", example = "10000000")
        @JsonAlias("total_prize_amount") Long totalPrizeAmount,
        @Schema(description = "대학생 전용 여부. 생략 가능.")
        Boolean isForEnrolledOnly,
        @Schema(description = "팀 모집 여부. 생략 가능.")
        Boolean isTeam
) {
    public AiCompetitionRequest {
        if (prizeInfo == null || prizeInfo.isBlank()) {
            prizeInfo = "상금 미정";
        }
        if (urls != null) {
            // OFFICIAL/RECRUITMENT may arrive as {"type":..., "url": null} when the AI couldn't find
            // one for that type; SOURCE_ORIGINAL is always present. Drop the empty ones instead of
            // rejecting the whole request — @NotEmpty below still rejects if nothing real is left.
            urls = urls.stream()
                    .filter(url -> url != null && url.url() != null && !url.url().isBlank())
                    .toList();
        }
        // Only auto-pick a primary URL when every remaining entry omitted the field entirely (the AI's
        // actual payload shape); an explicit true/false anywhere means the caller meant it and must be respected.
        if (urls != null && !urls.isEmpty() && urls.stream().allMatch(url -> url.primary() == null)) {
            List<CompetitionUrlRequest> original = urls;
            urls = IntStream.range(0, original.size())
                    .mapToObj(i -> i == 0
                            ? new CompetitionUrlRequest(original.get(i).type(), original.get(i).url(), true)
                            : original.get(i))
                    .toList();
        }
    }

    /** The entity stores a single category; the first element of the AI's array is the representative one. */
    public Competition.Category primaryCategory() {
        return category == null || category.isEmpty() ? null : category.get(0);
    }
}
