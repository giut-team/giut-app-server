package com.giut.server.dto.auth.request;

import jakarta.validation.constraints.NotBlank;

public record AppleLoginRequest(
        @NotBlank(message = "Identity token은 필수입니다")
        String identityToken,
        String authorizationCode,
        String fullName // 첫 로그인 시에만 제공됨
) {}
