package com.giut.server.dto.profile.request;

import com.giut.server.entity.ProfileLink;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "프로필 외부 링크")
public record ProfileLinkRequest(
        @Schema(description = "링크 유형", example = "GITHUB", allowableValues = {"GITHUB", "NOTION", "PORTFOLIO_PDF", "WEBSITE"})
        @NotNull(message = "링크 유형은 필수입니다.")
        ProfileLink.Type type,

        @Schema(description = "외부 링크 URL", example = "https://github.com/giut")
        @NotBlank(message = "링크 URL은 필수입니다.")
        @Pattern(regexp = "https?://.+", message = "링크 URL은 http 또는 https로 시작해야 합니다.")
        @Size(max = 2048, message = "링크 URL은 2048자 이하여야 합니다.")
        String url,

        @Schema(description = "링크 표시 제목", example = "GitHub")
        @Size(max = 100, message = "링크 제목은 100자 이하여야 합니다.")
        String title
) {
}
