package com.giut.server.competition.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CompetitionExtractionPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(CompetitionExtractionConfig.class);

    @Test
    void defaultsAcceptAboutOneMinuteAiCallWithLongerLease() {
        runner.run(context -> {
            CompetitionExtractionProperties properties = context.getBean(CompetitionExtractionProperties.class);
            assertThat(properties.callTimeout()).isEqualTo(Duration.ofSeconds(90));
            assertThat(properties.callTimeout()).isGreaterThan(Duration.ofMinutes(1));
            assertThat(properties.leaseDuration())
                    .isGreaterThanOrEqualTo(properties.callTimeout().plus(CompetitionExtractionProperties.MIN_LEASE_MARGIN));
            assertThat(properties.pendingTimeout()).isGreaterThan(properties.leaseDuration());
            assertThat(properties.concurrency()).isEqualTo(2);
            assertThat(properties.workerEnabled()).isTrue();
        });
    }

    @Test
    void rejectsLeaseShorterThanCallTimeoutPlusMargin() {
        runner.withPropertyValues("app.ai-extraction.jobs.call-timeout=120s", "app.ai-extraction.jobs.lease-duration=140s")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("lease-duration"));
        runner.withPropertyValues("app.ai-extraction.jobs.concurrency=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.ai-extraction.jobs.call-timeout=3m", "app.ai-extraction.jobs.lease-duration=4m")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void retryDelayDoublesAndIsCapped() {
        CompetitionExtractionProperties properties = new CompetitionExtractionProperties(true, 2, Duration.ofSeconds(2),
                Duration.ofSeconds(90), Duration.ofSeconds(180), 5, Duration.ofSeconds(30), Duration.ofMinutes(2),
                Duration.ofMinutes(15), Duration.ofHours(24), Duration.ofDays(30), 3, 20, 50, 100);
        assertThat(properties.retryDelay(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.retryDelay(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.retryDelay(3)).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.retryDelay(30)).isEqualTo(Duration.ofMinutes(2));
    }
}
