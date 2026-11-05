package com.giut.server.competition.util;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
// 대소문자·기본 포트 생략·끝 슬래시처럼 사실상 같은 URL이 다른 문자열로 저장돼 중복 등록을
// 놓치는 걸 막기 위한 정규화다. 정규화된 문자열 대신 SHA-256 해시를 저장/비교하는 이유는
// URL 길이가 가변적이라 그대로는 unique 인덱스를 걸기 번거롭기 때문(고정 길이 해시가 인덱스에 유리).
public class CompetitionUrlNormalizer {

    public NormalizedUrl normalize(String rawUrl) {
        try {
            URI uri = new URI(rawUrl.trim()).normalize();
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null || host == null || uri.getRawUserInfo() != null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("http 또는 https 형식의 URL만 등록할 수 있습니다.");
            }

            String normalizedScheme = scheme.toLowerCase();
            String normalizedHost = host.toLowerCase();
            int port = uri.getPort();
            boolean defaultPort = (normalizedScheme.equals("http") && port == 80)
                    || (normalizedScheme.equals("https") && port == 443);
            String portPart = port < 0 || defaultPort ? "" : ":" + port;

            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            String queryPart = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            String normalizedUrl = normalizedScheme + "://" + normalizedHost + portPart + path + queryPart;
            return new NormalizedUrl(normalizedUrl, sha256(normalizedUrl));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("올바른 URL 형식이 아닙니다.");
        }
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte current : hash) {
                hex.append(String.format("%02x", current));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("URL 해시 생성에 실패했습니다.", e);
        }
    }

    public record NormalizedUrl(String value, String hash) {
    }
}
