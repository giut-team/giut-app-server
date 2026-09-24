package com.giut.server.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.giut.server.dto.auth.response.LoginResponse;
import com.giut.server.entity.User;
import com.giut.server.exception.AuthenticationFailedException;
import com.giut.server.exception.ConflictException;
import com.giut.server.exception.AppleApiException;
import com.giut.server.repository.UserRepository;
import com.giut.server.security.AppleJwtValidator;
import com.giut.server.security.JwtProvider;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppleService {

    private static final String APPLE_AUTHORIZATION_URL = "https://appleid.apple.com/auth/authorize";

    private final AppleJwtValidator appleJwtValidator;
    private final AppleTokenClient appleTokenClient;
    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Value("${apple.client-id}")
    private String appleClientId;

    @Value("${apple.redirect-uri}")
    private String appleRedirectUri;

    public String getAuthorizationUrl(String state, String nonce) {
        return UriComponentsBuilder
                .fromUriString(APPLE_AUTHORIZATION_URL)
                .queryParam("client_id", appleClientId)
                .queryParam("redirect_uri", appleRedirectUri)
                .queryParam("response_type", "code id_token")
                .queryParam("response_mode", "form_post")
                .queryParam("scope", "name email")
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .build()
                .toUriString();
    }

    @Transactional
    public LoginResponse loginWithApple(String authorizationCode, String identityToken, String fullName, String expectedNonce) {
        try {
            Map<String, Object> callbackClaims = appleJwtValidator.validateAndGetClaims(identityToken, expectedNonce);
            AppleTokenClient.AppleTokenResponse tokenResponse = appleTokenClient.exchangeAuthorizationCode(authorizationCode);
            Map<String, Object> claims = appleJwtValidator.validateAndGetClaims(tokenResponse.identityToken());

            String callbackAppleUserId = appleJwtValidator.extractAppleUserId(callbackClaims);
            String appleUserId = appleJwtValidator.extractAppleUserId(claims);
            if (!callbackAppleUserId.equals(appleUserId)) {
                throw new AuthenticationFailedException("Apple 사용자 정보가 일치하지 않습니다.");
            }

            String email = appleJwtValidator.extractEmail(claims);
            Optional<User> existingUser = userRepository
                    .findByOauthProviderAndOauthProviderId(User.OAuthProvider.APPLE, appleUserId);

            User user = existingUser.orElseGet(() -> {
                if (email == null || email.isBlank()) {
                    throw new AuthenticationFailedException("Apple 계정 이메일 정보를 확인할 수 없습니다.");
                }
                return createAppleUser(appleUserId, email, fullName);
            });

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
        } catch (AuthenticationFailedException | ConflictException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new AuthenticationFailedException("유효하지 않은 Apple identity token입니다.");
        } catch (Exception e) {
            log.error("Apple login failed", e);
            throw new AppleApiException(HttpStatus.BAD_GATEWAY, "Apple 로그인 처리에 실패했습니다.");
        }
    }

    private User createAppleUser(String appleUserId, String email, String fullName) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("이미 동일한 이메일로 가입된 계정이 있습니다.");
        }

        String nickname = fullName != null && !fullName.isBlank()
                ? fullName
                : "기웃 사용자";

        return userRepository.save(User.createOAuthUser(
                email,
                nickname,
                User.OAuthProvider.APPLE,
                appleUserId
        ));
    }

    public String extractFullName(String userJson) {
        if (userJson == null || userJson.isBlank()) {
            return null;
        }

        try {
            JsonObject user = JsonParser.parseString(userJson).getAsJsonObject();
            if (!user.has("name") || !user.get("name").isJsonObject()) {
                return null;
            }

            JsonObject name = user.getAsJsonObject("name");
            String firstName = name.has("firstName") && !name.get("firstName").isJsonNull()
                    ? name.get("firstName").getAsString().trim()
                    : "";
            String lastName = name.has("lastName") && !name.get("lastName").isJsonNull()
                    ? name.get("lastName").getAsString().trim()
                    : "";
            String fullName = (lastName + firstName).trim();
            return fullName.isBlank() ? null : fullName;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Apple 사용자 이름 형식이 올바르지 않습니다.");
        }
    }
}
