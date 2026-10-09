package com.giut.server.global.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.giut.server.global.alert.DiscordAlertService.TestAlertResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

class DiscordAlertTestControllerTest {

    @Test
    void mapsDeliveryOutcomesToSafeHttpResponses() {
        DiscordAlertService service = mock(DiscordAlertService.class);
        DiscordAlertTestController controller = new DiscordAlertTestController(service);

        when(service.sendTestAlert()).thenReturn(TestAlertResult.SENT);
        var sentResponse = controller.sendTestAlert();
        assertThat(sentResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sentResponse.getBody().getMessage()).doesNotContain("webhook");

        when(service.sendTestAlert()).thenReturn(TestAlertResult.NOT_CONFIGURED);
        assertThat(controller.sendTestAlert().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        when(service.sendTestAlert()).thenReturn(TestAlertResult.RATE_LIMITED);
        assertThat(controller.sendTestAlert().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        when(service.sendTestAlert()).thenReturn(TestAlertResult.FAILED);
        assertThat(controller.sendTestAlert().getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void controllerExistsOnlyWhenDevelopmentEnvironmentIsExplicitlyEnabled() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(ControllerConfiguration.class)
                .withBean(DiscordAlertService.class, () -> mock(DiscordAlertService.class));

        contextRunner.run(context -> assertThat(context).doesNotHaveBean(DiscordAlertTestController.class));
        contextRunner.withPropertyValues("APP_ENVIRONMENT=dev")
                .run(context -> assertThat(context).hasSingleBean(DiscordAlertTestController.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(DiscordAlertTestController.class)
    static class ControllerConfiguration {
    }
}
