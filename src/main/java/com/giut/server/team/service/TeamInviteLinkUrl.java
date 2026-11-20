package com.giut.server.team.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Component
class TeamInviteLinkUrl {

    private static final Logger log = LoggerFactory.getLogger(TeamInviteLinkUrl.class);
    private final URI frontendBaseUrl;

    TeamInviteLinkUrl(@Value("${app.team-invite.frontend-base-url:}") String configuredUrl, Environment environment) {
        boolean local = environment.matchesProfiles("local");
        if (configuredUrl.isBlank()) {
            frontendBaseUrl = local ? URI.create("http://localhost:3000") : null;
            if (!local) {
                log.warn("APP_TEAM_INVITE_FRONTEND_BASE_URL is not configured. Team invite link issuance is unavailable.");
            }
            return;
        }
        frontendBaseUrl = validate(configuredUrl.trim(), local);
    }

    String build(Long teamId, String token) {
        if (frontendBaseUrl == null) {
            throw new IllegalStateException("APP_TEAM_INVITE_FRONTEND_BASE_URL is required to issue team invite links.");
        }
        return UriComponentsBuilder.fromUri(frontendBaseUrl)
                .pathSegment("teams", teamId.toString(), "invite")
                .fragment("token=" + token)
                .build(true).toUriString();
    }

    private static URI validate(String value, boolean local) {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("APP_TEAM_INVITE_FRONTEND_BASE_URL must be a valid URI.");
        }
        String host = uri.getHost();
        if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new IllegalStateException("APP_TEAM_INVITE_FRONTEND_BASE_URL must be a frontend base URL.");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
        boolean loopback = normalizedHost.equals("localhost") || normalizedHost.endsWith(".localhost")
                || normalizedHost.startsWith("127.") || normalizedHost.equals("[::1]")
                || normalizedHost.equals("[0:0:0:0:0:0:0:1]");
        if (!(local && loopback && "http".equalsIgnoreCase(uri.getScheme()))
                && (!"https".equalsIgnoreCase(uri.getScheme()) || (!local && loopback))) {
            throw new IllegalStateException("APP_TEAM_INVITE_FRONTEND_BASE_URL must use a non-local HTTPS address outside local.");
        }
        return uri;
    }
}
