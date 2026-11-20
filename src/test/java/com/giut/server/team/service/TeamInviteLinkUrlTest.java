package com.giut.server.team.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamInviteLinkUrlTest {

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void missingAddressWarnsAtStartupAndFailsIssuanceOutsideLocalProfile(CapturedOutput output) {
        var urls = new TeamInviteLinkUrl("", new MockEnvironment());

        assertThat(output).contains("WARN").contains("APP_TEAM_INVITE_FRONTEND_BASE_URL is not configured");
        assertThatThrownBy(() -> urls.build(1L, "sample"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_TEAM_INVITE_FRONTEND_BASE_URL");
    }

    @Test
    void localProfileUsesLoopbackDefault() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertThat(new TeamInviteLinkUrl("", environment).build(1L, "sample"))
                .isEqualTo("http://localhost:3000/teams/1/invite#token=sample");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://localhost:3000", "https://127.0.0.1:3000", "https://[::1]:3000",
            "http://example.com", "https://example.com?query=value", "https://example.com#fragment"})
    void invalidDeployedAddressFailsValidation(String url) {
        assertThatThrownBy(() -> new TeamInviteLinkUrl(url, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_TEAM_INVITE_FRONTEND_BASE_URL");
    }
}
