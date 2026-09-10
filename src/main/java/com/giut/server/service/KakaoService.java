package com.giut.server.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.giut.server.dto.auth.response.LoginResponse;
import com.giut.server.entity.User;
import com.giut.server.exception.AuthenticationFailedException;
import com.giut.server.exception.ConflictException;
import com.giut.server.exception.KakaoApiException;
import com.giut.server.repository.UserRepository;
import com.giut.server.security.JwtProvider;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class KakaoService {

    private static final String KAKAO_AUTHORIZATION_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String KAKAO_TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    @Value("${kakao.rest-api-key}")
    private String restApiKey;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    public String getAuthorizationUrl() {
        return UriComponentsBuilder
                .fromUriString(KAKAO_AUTHORIZATION_URL)
                .queryParam("client_id", restApiKey)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .build()
                .toUriString();
    }

    @Transactional
    public LoginResponse login(String authorizationCode) {
        String kakaoAccessToken = getAccessToken(authorizationCode);
        KakaoUserInfo kakaoUserInfo = getUserInfo(kakaoAccessToken);

        User user = userRepository.findByOauthProviderAndOauthProviderId(
                        User.OAuthProvider.KAKAO,
                        kakaoUserInfo.providerId()
                )
                .orElseGet(() -> createKakaoUser(kakaoUserInfo));

        if (user.getStatus() != User.Status.ACTIVE) {
            throw new AuthenticationFailedException("로그인할 수 없는 사용자 상태입니다.");
        }

        return new LoginResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                jwtProvider.generateAccessToken(user),
                jwtProvider.generateRefreshToken(user),
                "Bearer"
        );
    }

    private User createKakaoUser(KakaoUserInfo kakaoUserInfo) {
        if (userRepository.existsByEmail(kakaoUserInfo.email())) {
            throw new ConflictException("이미 동일한 이메일로 가입된 계정이 있습니다.");
        }

        User user = User.createOAuthUser(
                kakaoUserInfo.email(),
                kakaoUserInfo.nickname(),
                User.OAuthProvider.KAKAO,
                kakaoUserInfo.providerId()
        );
        return userRepository.save(user);
    }

    private String getAccessToken(String authorizationCode) {
        HttpURLConnection connection = openConnection(KAKAO_TOKEN_URL, "POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");

        String form = "grant_type=authorization_code"
                + "&client_id=" + encode(restApiKey)
                + "&redirect_uri=" + encode(redirectUri)
                + "&code=" + encode(authorizationCode);

        try (Writer writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(form);
        } catch (IOException e) {
            throw new KakaoApiException(HttpStatus.BAD_GATEWAY, "카카오 토큰 요청에 실패했습니다.");
        }

        JsonObject tokenResponse = getJsonResponse(connection, "카카오 토큰 발급에 실패했습니다.");
        return requiredString(tokenResponse, "access_token", "카카오 액세스 토큰이 없습니다.");
    }

    private KakaoUserInfo getUserInfo(String accessToken) {
        HttpURLConnection connection = openConnection(KAKAO_USER_INFO_URL, "GET");
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);

        JsonObject response = getJsonResponse(connection, "카카오 사용자 정보 조회에 실패했습니다.");
        String providerId = requiredString(response, "id", "카카오 사용자 식별자가 없습니다.");

        JsonObject properties = response.has("properties") && response.get("properties").isJsonObject()
                ? response.getAsJsonObject("properties")
                : new JsonObject();
        String nickname = optionalString(properties, "nickname", "기웃 사용자");

        JsonObject kakaoAccount = response.has("kakao_account") && response.get("kakao_account").isJsonObject()
                ? response.getAsJsonObject("kakao_account")
                : new JsonObject();
        String email = optionalString(kakaoAccount, "email", null);
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("카카오 이메일 제공 동의가 필요합니다.");
        }

        return new KakaoUserInfo(providerId, email, nickname);
    }

    private HttpURLConnection openConnection(String requestUrl, String method) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(requestUrl).toURL().openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            return connection;
        } catch (IOException e) {
            throw new KakaoApiException(HttpStatus.BAD_GATEWAY, "카카오 서버에 연결할 수 없습니다.");
        }
    }

    private JsonObject getJsonResponse(HttpURLConnection connection, String failureMessage) {
        try {
            int status = connection.getResponseCode();
            InputStream inputStream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            if (status < 200 || status >= 300) {
                throw new KakaoApiException(HttpStatus.BAD_GATEWAY, failureMessage);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String responseBody = reader.lines().reduce("", String::concat);
                return JsonParser.parseString(responseBody).getAsJsonObject();
            }
        } catch (KakaoApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new KakaoApiException(HttpStatus.BAD_GATEWAY, failureMessage);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String requiredString(JsonObject jsonObject, String key, String errorMessage) {
        String value = optionalString(jsonObject, key, null);
        if (value == null || value.isBlank()) {
            throw new KakaoApiException(HttpStatus.BAD_GATEWAY, errorMessage);
        }
        return value;
    }

    private String optionalString(JsonObject jsonObject, String key, String defaultValue) {
        JsonElement value = jsonObject.get(key);
        return value == null || value.isJsonNull() ? defaultValue : value.getAsString();
    }

    private record KakaoUserInfo(String providerId, String email, String nickname) {
    }
}
