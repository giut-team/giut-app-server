package com.giut.server.competition.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.giut.server.competition.entity.Competition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

@Schema(description = "관리자 공모전 등록 요청. 검증 상태는 서버가 MANUALLY_VERIFIED로 설정하며 입력받지 않습니다.")
@JsonIgnoreProperties("verificationStatus") // Ignore retired input from older clients; never bind it.
public record CreateCompetitionRequest(
        @NotBlank(message = "공모전 제목은 필수입니다.")
        @Size(max = 200, message = "공모전 제목은 200자 이하여야 합니다.")
        String title,

        @NotNull(message = "공모전 카테고리는 필수입니다.")
        Competition.Category category,

        @Size(max = 150, message = "주최 기관은 150자 이하여야 합니다.")
        String hostOrganization,

        @Size(max = 3000, message = "참가 대상은 3000자 이하여야 합니다.")
        String targetParticipants,

        @NotBlank(message = "공모전 소개는 필수입니다.")
        @Size(max = 10000, message = "공모전 소개는 10000자 이하여야 합니다.")
        String summary,

        Instant applicationStartAt,
        @NotNull(message = "모집 마감 일시는 필수입니다.")
        Instant applicationEndAt,

        @Schema(description = "게시 상태", example = "DRAFT")
        @NotNull(message = "게시 상태는 필수입니다.")
        Competition.PublicationStatus publicationStatus,

        @NotEmpty(message = "공모전 URL은 하나 이상 등록해야 합니다.")
        List<@Valid CompetitionUrlRequest> urls,

        @Schema(description = "활동 유형. 생략하거나 null이면 공모전으로 저장합니다.", example = "HACKATHON")
        Competition.ActivityType activityType,

        @Size(max = 3000, message = "상금 정보는 3000자 이하여야 합니다.") String prizeInfo,

        @Schema(description = "총상금 숫자. 미입력 시 null입니다.", example = "10000000") Long totalPrizeAmount
) {
    public CreateCompetitionRequest(String title, Competition.Category category, String hostOrganization,
            String targetParticipants, String summary, Instant applicationStartAt, Instant applicationEndAt,
            Competition.PublicationStatus publicationStatus, List<CompetitionUrlRequest> urls) {
        this(title, category, hostOrganization, targetParticipants, summary, applicationStartAt,
                applicationEndAt, publicationStatus, urls, null, null, null);
    }

    public CreateCompetitionRequest(String title, Competition.Category category, String hostOrganization,
            String targetParticipants, String summary, Instant applicationStartAt, Instant applicationEndAt,
            Competition.PublicationStatus publicationStatus, List<CompetitionUrlRequest> urls, Competition.ActivityType activityType) {
        this(title, category, hostOrganization, targetParticipants, summary, applicationStartAt,
                applicationEndAt, publicationStatus, urls, activityType, null, null);
    }

    public UpsertCompetitionRequest toUpsertRequest() {
        return new UpsertCompetitionRequest(title, category, hostOrganization, targetParticipants, summary,
                applicationStartAt, applicationEndAt, publicationStatus, urls, activityType, prizeInfo, totalPrizeAmount);
    }

    /** Keeps existing sample payloads and their verified default unchanged. */
    public static CreateCompetitionRequest from(UpsertCompetitionRequest request) {
        return new CreateCompetitionRequest(request.title(), request.category(), request.hostOrganization(),
                request.targetParticipants(), request.summary(), request.applicationStartAt(), request.applicationEndAt(),
                request.publicationStatus(), request.urls(), request.activityType(), request.prizeInfo(), request.totalPrizeAmount());
    }
}
