package com.giut.server.service;

import com.giut.server.dto.auth.response.LoginResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
public class OAuthCookieService {

    private static final String KAKAO_STATE_COOKIE = "giut_kakao_oauth_state";
    private static final String APPLE_STATE_COOKIE = "giut_apple_oauth_state";
    private static final String APPLE_NONCE_COOKIE = "giut_apple_oauth_nonce";
    private static final String ACCESS_TOKEN_COOKIE = "giut_access_token";
    private static final String REFRESH_TOKEN_COOKIE = "giut_refresh_token";

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.oauth.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${jwt.expiration-time}")
    private long accessTokenMaxAgeMillis;

    @Value("${jwt.refresh-expiration-time}")
    private long refreshTokenMaxAgeMillis;

    public String createKakaoState(HttpServletResponse response) {
        String state = randomValue();
        addCookie(response, KAKAO_STATE_COOKIE, state, "Lax", Duration.ofMinutes(5));
        return state;
    }

    public AppleAuthorization createAppleAuthorization(HttpServletResponse response) {
        String state = randomValue();
        String nonce = randomValue();

        addCookie(response, APPLE_STATE_COOKIE, state, "None", Duration.ofMinutes(5));
        addCookie(response, APPLE_NONCE_COOKIE, nonce, "None", Duration.ofMinutes(5));

        return new AppleAuthorization(state, nonce);
    }

    public void validateAndClearKakaoState(HttpServletRequest request, HttpServletResponse response, String state) {
        validateState(readCookie(request, KAKAO_STATE_COOKIE), state);
        clearCookie(response, KAKAO_STATE_COOKIE, "Lax");
    }

    public String validateAndClearAppleAuthorization(
            HttpServletRequest request,
            HttpServletResponse response,
            String state
    ) {
        validateState(readCookie(request, APPLE_STATE_COOKIE), state);
        String nonce = readCookie(request, APPLE_NONCE_COOKIE);
        if (nonce == null) {
            throw new IllegalArgumentException("Apple OAuth nonce를 찾을 수 없습니다.");
        }
        clearCookie(response, APPLE_STATE_COOKIE, "None");
        clearCookie(response, APPLE_NONCE_COOKIE, "None");
        return nonce;
    }

    public void writeLoginCookies(HttpServletResponse response, LoginResponse loginResponse) {
        addCookie(response, ACCESS_TOKEN_COOKIE, loginResponse.getAccessToken(), "Lax",
                Duration.ofMillis(accessTokenMaxAgeMillis));
        addCookie(response, REFRESH_TOKEN_COOKIE, loginResponse.getRefreshToken(), "Lax",
                Duration.ofMillis(refreshTokenMaxAgeMillis));
    }

    private void validateState(String expected, String actual) {
        if (expected == null || actual == null || !MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        )) {
            throw new IllegalArgumentException("OAuth state가 일치하지 않습니다.");
        }
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void clearCookie(HttpServletResponse response, String name, String sameSite) {
        addCookie(response, name, "", sameSite, Duration.ZERO);
    }

    private void addCookie(HttpServletResponse response, String name, String value, String sameSite, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String randomValue() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record AppleAuthorization(String state, String nonce) {
    }
}
