package com.giut.server.dto.competition.request;

import com.giut.server.entity.CompetitionUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CompetitionUrlRequest(
        @Schema(description = "공모전 URL 종류", example = "RECRUITMENT")
        @NotNull(message = "URL 종류는 필수입니다.")
        CompetitionUrl.Type type,

        @Schema(description = "외부 URL", example = "https://example.com/recruitment")
        @NotBlank(message = "공모전 URL은 필수입니다.")
        @Size(max = 2000, message = "공모전 URL은 2000자 이하여야 합니다.")
        @URL(message = "올바른 URL 형식이 아닙니다.")
        String url,

        @Schema(description = "대표 URL 여부", example = "true")
        boolean primary
) {
}
