package com.giut.server.dto.profile.request;

import com.giut.server.entity.ProfileReport;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "프로필 신고 요청. 추가 설명은 모든 신고 사유에서 생략하거나 null로 보낼 수 있습니다.")
public record CreateProfileReportRequest(
        @Schema(description = "신고 사유", example = "FALSE_INFORMATION_IMPERSONATION")
        @NotNull(message = "신고 사유는 필수입니다.")
        ProfileReport.Reason reason,

        @Schema(description = "추가 설명 (선택)", example = "타인의 경력을 본인 경력으로 적었습니다.", nullable = true)
        @Size(max = 1000, message = "추가 설명은 1000자 이하여야 합니다.")
        String description
) {
}
