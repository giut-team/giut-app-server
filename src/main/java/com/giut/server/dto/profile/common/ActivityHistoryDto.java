package com.giut.server.dto.profile.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.giut.server.entity.ActivityHistory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.YearMonth;

@Schema(description = "활동 이력 생성·수정 요청 및 응답")
public record ActivityHistoryDto(
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @Schema(description = "활동 이력 ID", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
        Long id,

        @NotNull(message = "활동 구분은 필수입니다.")
        @Schema(description = "활동 구분", example = "AWARD", allowableValues = {"AWARD", "EXTERNAL_ACTIVITY", "CLUB", "INTERNSHIP", "CERTIFICATION"})
        ActivityHistory.Category category,

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @Schema(description = "활동 구분 표시명", example = "수상", accessMode = Schema.AccessMode.READ_ONLY)
        String categoryName,

        @NotBlank(message = "활동명은 필수입니다.")
        @Size(max = 100, message = "활동명은 100자 이하여야 합니다.")
        @Schema(description = "활동명", example = "서울시 데이터 활용 공모전 우수상")
        String title,

        @NotBlank(message = "기관·주최는 필수입니다.")
        @Size(max = 100, message = "기관·주최는 100자 이하여야 합니다.")
        @Schema(description = "기관 또는 주최", example = "서울특별시")
        String organization,

        @NotNull(message = "활동 시작월은 필수입니다.")
        @JsonFormat(pattern = "yyyy-MM")
        @Schema(description = "활동 시작월", example = "2025-03", type = "string", format = "year-month")
        YearMonth startMonth,

        @NotNull(message = "활동 종료월은 필수입니다.")
        @JsonFormat(pattern = "yyyy-MM")
        @Schema(description = "활동 종료월", example = "2025-06", type = "string", format = "year-month")
        YearMonth endMonth
) {
    public static ActivityHistoryDto from(ActivityHistory activityHistory) {
        return new ActivityHistoryDto(
                activityHistory.getId(),
                activityHistory.getCategory(),
                activityHistory.getCategory().getDisplayName(),
                activityHistory.getTitle(),
                activityHistory.getOrganization(),
                activityHistory.getStartMonth(),
                activityHistory.getEndMonth()
        );
    }
}
