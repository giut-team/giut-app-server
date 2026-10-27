package com.giut.server.profile.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileReportAlertConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(WebClient.class, WebClient::create)
            .withBean(ProfileReportAlertService.class);

    @Test
    void usesExistingWebhookByDefault() {
        runner.withPropertyValues("DISCORD_ALERT_WEBHOOK_URL=" + ProfileReportAlertServiceTest.WEBHOOK)
                .run(context -> {
                    var service = context.getBean(ProfileReportAlertService.class);
                    assertThat(ReflectionTestUtils.getField(service, "webhook"))
                            .isEqualTo(URI.create(ProfileReportAlertServiceTest.WEBHOOK));
                    assertThat(ReflectionTestUtils.getField(service, "enabled")).isEqualTo(true);
                });
    }

    @Test
    void dedicatedWebhookAndDisableFlagOverrideDefaults() {
        runner.withPropertyValues("DISCORD_ALERT_WEBHOOK_URL=" + ProfileReportAlertServiceTest.WEBHOOK,
                        "DISCORD_PROFILE_REPORT_WEBHOOK_URL=https://discord.com/api/webhooks/123/DEDICATED",
                        "DISCORD_PROFILE_REPORT_ENABLED=false")
                .run(context -> {
                    var service = context.getBean(ProfileReportAlertService.class);
                    assertThat(ReflectionTestUtils.getField(service, "webhook"))
                            .isEqualTo(URI.create("https://discord.com/api/webhooks/123/DEDICATED"));
                    assertThat(ReflectionTestUtils.getField(service, "enabled")).isEqualTo(false);
                });
    }

    @Test
    void absentWebhookDoesNotPreventStartup() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(ReflectionTestUtils.getField(context.getBean(ProfileReportAlertService.class), "webhook"))
                    .isNull();
        });
    }
}
