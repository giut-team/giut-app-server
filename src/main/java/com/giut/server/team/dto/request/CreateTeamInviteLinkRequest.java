package com.giut.server.team.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeamInviteLinkRequest(
        @Schema(description = "초대 링크로 합류할 모집 분야", example = "BACKEND_DEVELOPER")
        @NotBlank @Size(max = 40) String roleCode
) {}
