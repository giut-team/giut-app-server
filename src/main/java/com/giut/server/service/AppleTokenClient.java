package com.giut.server.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.giut.server.exception.AppleApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class AppleTokenClient {

    private static final String APPLE_TOKEN_URL = "https://appleid.apple.com/auth/token";

    private final WebClient webClient;
    private final AppleClientSecretProvider appleClientSecretProvider;

    @Value("${apple.client-id}")
    private String appleClientId;

    @Value("${apple.redirect-uri}")
    private String appleRedirectUri;

    public AppleTokenResponse exchangeAuthorizationCode(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", appleClientId);
        form.add("client_secret", appleClientSecretProvider.createClientSecret());
        form.add("code", authorizationCode);
        form.add("redirect_uri", appleRedirectUri);

        try {
            String responseBody = webClient.post()
                    .uri(APPLE_TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseBody == null) {
                throw new AppleApiException(HttpStatus.BAD_GATEWAY, "Apple 토큰 응답이 비어 있습니다.");
            }

            JsonObject response = JsonParser.parseString(responseBody).getAsJsonObject();
            if (!response.has("id_token") || response.get("id_token").isJsonNull()) {
                throw new AppleApiException(HttpStatus.BAD_GATEWAY, "Apple identity token이 없습니다.");
            }

            return new AppleTokenResponse(response.get("id_token").getAsString());
        } catch (AppleApiException e) {
            throw e;
        } catch (Exception e) {
            throw new AppleApiException(HttpStatus.BAD_GATEWAY, "Apple 토큰 교환에 실패했습니다.");
        }
    }

    public record AppleTokenResponse(String identityToken) {
    }
}
