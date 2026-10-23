package com.giut.server.global.alert;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class SlowApiAlertConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withBean(WebClient.class, WebClient::create);

    @Test
    void defaultsReuseExistingWebhookAndCanBeDisabledWithoutChangingErrorAlerts() {
        runner.withPropertyValues("DISCORD_ALERT_WEBHOOK_URL=https://discord.com/api/webhooks/123/FALLBACK")
                .run(context -> {
                    var service = context.getBean(SlowApiAlertService.class);
                    assertThat(service.isActive()).isTrue();
                    assertThat(ReflectionTestUtils.getField(service, "thresholdMillis")).isEqualTo(2000L);
                    assertThat(ReflectionTestUtils.getField(service, "webhook"))
                            .isEqualTo(URI.create("https://discord.com/api/webhooks/123/FALLBACK"));
                });
        runner.withPropertyValues("DISCORD_ALERT_WEBHOOK_URL=https://discord.com/api/webhooks/123/FALLBACK",
                        "DISCORD_SLOW_API_ENABLED=false")
                .run(context -> assertThat(context.getBean(SlowApiAlertService.class).isActive()).isFalse());
    }

    @Test
    void dedicatedWebhookAndThresholdOverrideFallback() {
        runner.withPropertyValues("DISCORD_ALERT_WEBHOOK_URL=https://discord.com/api/webhooks/123/FALLBACK",
                        "DISCORD_SLOW_API_WEBHOOK_URL=https://discord.com/api/webhooks/123/DEDICATED",
                        "DISCORD_SLOW_API_THRESHOLD_MS=3500", "DISCORD_SLOW_API_COOLDOWN_SECONDS=60")
                .run(context -> {
                    var service = context.getBean(SlowApiAlertService.class);
                    assertThat(ReflectionTestUtils.getField(service, "thresholdMillis")).isEqualTo(3500L);
                    assertThat(ReflectionTestUtils.getField(service, "webhook"))
                            .isEqualTo(URI.create("https://discord.com/api/webhooks/123/DEDICATED"));
                });
    }

    @Test
    void absentWebhookDoesNotStartDelivery() {
        runner.run(context -> assertThat(context.getBean(SlowApiAlertService.class).isActive()).isFalse());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import({SlowApiAlertService.class, SlowApiObservationHandler.class})
    static class Config {}
}
