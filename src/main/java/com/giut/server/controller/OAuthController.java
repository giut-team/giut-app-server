package com.giut.server.controller;

import com.giut.server.dto.auth.response.LoginResponse;
import com.giut.server.service.AppleService;
import com.giut.server.service.KakaoService;
import com.giut.server.service.OAuthCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "OAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/oauth")
public class OAuthController {

    private final KakaoService kakaoService;
    private final AppleService appleService;
    private final OAuthCookieService oauthCookieService;

    @Value("${app.oauth.success-redirect-uri}")
    private String oauthSuccessRedirectUri;

    @GetMapping("/kakao/authorization")
    @Operation(summary = "카카오 인가 페이지로 이동", description = "OAuth state를 생성한 뒤 카카오 로그인 화면으로 이동합니다.")
    @ApiResponse(responseCode = "302", description = "카카오 인가 페이지로 이동")
    public ResponseEntity<Void> redirectToKakaoAuthorization(HttpServletResponse response) {
        String state = oauthCookieService.createKakaoState(response);
        return redirect(kakaoService.getAuthorizationUrl(state));
    }

    @GetMapping("/kakao/callback")
    @Operation(summary = "카카오 OAuth callback", description = "카카오가 전달한 인가 코드를 백엔드에서 교환하고, 기웃 JWT 쿠키를 발급한 뒤 프론트로 이동합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "로그인 성공 후 프론트 페이지로 이동"),
            @ApiResponse(responseCode = "400", description = "인가 코드 또는 OAuth state가 올바르지 않음"),
            @ApiResponse(responseCode = "409", description = "동일한 이메일로 이미 가입된 계정 존재"),
            @ApiResponse(responseCode = "502", description = "카카오 서버 통신 실패")
    })
    public ResponseEntity<Void> kakaoCallback(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        oauthCookieService.validateAndClearKakaoState(request, response, state);
        LoginResponse loginResponse = kakaoService.login(code);
        return completeLogin(response, loginResponse);
    }

    @GetMapping("/apple/authorization")
    @Operation(summary = "Apple 인가 페이지로 이동", description = "OAuth state와 nonce를 생성한 뒤 Apple 로그인 화면으로 이동합니다.")
    @ApiResponse(responseCode = "302", description = "Apple 인가 페이지로 이동")
    public ResponseEntity<Void> redirectToAppleAuthorization(HttpServletResponse response) {
        OAuthCookieService.AppleAuthorization authorization = oauthCookieService.createAppleAuthorization(response);
        return redirect(appleService.getAuthorizationUrl(authorization.state(), authorization.nonce()));
    }

    @PostMapping(path = "/apple/callback", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "Apple OAuth callback", description = "Apple이 form POST로 전달한 코드와 identity token을 검증하고, 기웃 JWT 쿠키를 발급한 뒤 프론트로 이동합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "로그인 성공 후 프론트 페이지로 이동"),
            @ApiResponse(responseCode = "400", description = "Apple callback 값 또는 OAuth state가 올바르지 않음"),
            @ApiResponse(responseCode = "401", description = "유효하지 않거나 만료된 Apple identity token"),
            @ApiResponse(responseCode = "409", description = "동일한 이메일로 이미 가입된 계정 존재"),
            @ApiResponse(responseCode = "502", description = "Apple 토큰 교환 또는 공개키 조회 실패")
    })
    public ResponseEntity<Void> appleCallback(
            @RequestParam("code") String code,
            @RequestParam("id_token") String identityToken,
            @RequestParam String state,
            @RequestParam(value = "user", required = false) String user,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String nonce = oauthCookieService.validateAndClearAppleAuthorization(request, response, state);
        LoginResponse loginResponse = appleService.loginWithApple(
                code,
                identityToken,
                appleService.extractFullName(user),
                nonce
        );
        return completeLogin(response, loginResponse);
    }

    private ResponseEntity<Void> completeLogin(HttpServletResponse response, LoginResponse loginResponse) {
        oauthCookieService.writeLoginCookies(response, loginResponse);
        return redirect(oauthSuccessRedirectUri);
    }

    private ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(location))
                .build();
    }
}
