package com.giut.server.profile.dto.request;

import com.giut.server.profile.entity.ProfileReport;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 프로필 신고 검토 요청")
public record ReviewProfileReportRequest(
        @Schema(description = "검토 결과. PENDING으로 되돌릴 수 없습니다.", example = "DISMISSED",
                allowableValues = {"ACTIONED", "DISMISSED"})
        @NotNull(message = "검토 결과는 필수입니다.")
        ProfileReport.Status status,

        @Schema(description = "관리자 판단 근거", example = "제출된 내용만으로는 허위 사실을 확인할 수 없습니다.")
        @NotBlank(message = "검토 메모는 필수입니다.")
        @Size(max = 1000, message = "검토 메모는 1000자 이하여야 합니다.")
        String reviewNote
) {
}
