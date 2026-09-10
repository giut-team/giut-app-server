package com.giut.server.security;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AppleJwtValidator {
    private static final String APPLE_PUBLIC_KEYS_URL = "https://appleid.apple.com/auth/keys";

    @Value("${apple.bundle-id}")
    private String appleBundleId;

    private final WebClient webClient;

    public AppleJwtValidator(WebClient webClient) {
        this.webClient = webClient;
    }

    public Map<String, Object> validateAndGetClaims(String identityToken) throws Exception {
        SignedJWT signedJWT = SignedJWT.parse(identityToken);

        if (!verifySignature(signedJWT)) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }

        Map<String, Object> claims = signedJWT.getJWTClaimsSet().getClaims();

        validateAudience(claims);
        validateExpiration(signedJWT);
        validateIssuer(claims);

        return claims;
    }

    private void validateAudience(Map<String, Object> claims) {
        Object audClaim = claims.get("aud");
        boolean valid = false;

        if (audClaim instanceof String) {
            valid = appleBundleId.equals(audClaim);
        } else if (audClaim instanceof List) {
            valid = ((List<?>) audClaim).contains(appleBundleId);
        }

        if (!valid) {
            throw new IllegalArgumentException("Invalid audience: " + audClaim);
        }
    }

    private void validateExpiration(SignedJWT signedJWT) throws Exception {
        Date exp = signedJWT.getJWTClaimsSet().getExpirationTime();
        if (exp == null || exp.before(new Date())) {
            throw new IllegalArgumentException("Token expired");
        }
    }

    private void validateIssuer(Map<String, Object> claims) {
        String issuer = (String) claims.get("iss");
        if (!"https://appleid.apple.com".equals(issuer)) {
            throw new IllegalArgumentException("Invalid issuer: " + issuer);
        }
    }

    private boolean verifySignature(SignedJWT signedJWT) throws Exception {
        String jwksJson = webClient.get()
                .uri(APPLE_PUBLIC_KEYS_URL)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (jwksJson == null) {
            throw new IllegalStateException("Failed to fetch Apple public keys");
        }

        JWKSet jwkSet = JWKSet.parse(jwksJson);
        JWK jwk = jwkSet.getKeyByKeyId(signedJWT.getHeader().getKeyID());

        if (jwk == null) {
            throw new IllegalArgumentException("Public key not found for kid");
        }

        RSAKey rsaKey = jwk.toRSAKey();
        JWSVerifier verifier = new RSASSAVerifier(rsaKey);

        return signedJWT.verify(verifier);
    }

    public String extractAppleUserId(Map<String, Object> claims) {
        return (String) claims.get("sub");
    }

    public String extractEmail(Map<String, Object> claims) {
        return (String) claims.get("email");
    }
}
