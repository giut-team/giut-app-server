package com.giut.server.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Component
public class AppleClientSecretProvider {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    @Value("${apple.client-id}")
    private String appleClientId;

    @Value("${apple.team-id:}")
    private String appleTeamId;

    @Value("${apple.key-id:}")
    private String appleKeyId;

    @Value("${apple.private-key-path:}")
    private String applePrivateKeyPath;

    public String createClientSecret() {
        validateConfiguration();

        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(appleTeamId)
                    .subject(appleClientId)
                    .audience(APPLE_ISSUER)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(60 * 60 * 24 * 180)))
                    .build();

            SignedJWT clientSecret = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(appleKeyId).build(),
                    claims
            );
            clientSecret.sign(new ECDSASigner(readPrivateKey()));
            return clientSecret.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Apple client secret 생성에 실패했습니다.", e);
        }
    }

    private ECPrivateKey readPrivateKey() throws Exception {
        String pem = Files.readString(Path.of(applePrivateKeyPath));
        String encoded = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(encoded);
        return (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private void validateConfiguration() {
        if (appleTeamId.isBlank() || appleKeyId.isBlank() || applePrivateKeyPath.isBlank()) {
            throw new IllegalStateException(
                    "Apple OAuth 설정이 필요합니다. apple.team-id, apple.key-id, apple.private-key-path를 설정하세요."
            );
        }
    }
}
