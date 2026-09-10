package com.giut.server.dto.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoLoginRequest {

    @NotBlank(message = "카카오 인가 코드는 필수입니다.")
    @Schema(description = "카카오가 Redirect URI로 전달한 인가 코드", example = "authorization_code")
    private String code;
}
