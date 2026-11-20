package com.giut.server.team.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TeamInviteTokenRequest(
        @Schema(description = "공유 링크에서 읽은 초대 토큰")
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token
) {}
