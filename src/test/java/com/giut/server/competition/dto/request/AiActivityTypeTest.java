package com.giut.server.competition.dto.request;

import com.giut.server.competition.entity.Competition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiActivityTypeTest {
    @ParameterizedTest
    @CsvSource({"CONTEST,COMPETITION", "COMPETITION,CONTEST", "HACKATHON,HACKATHON", "SUPPORTERS,SUPPORTERS",
            "VOLUNTEER,VOLUNTEERING", "STARTUP,STARTUP", "EDUCATION,EDUCATION_PROGRAM",
            "OVERSEAS_EXPERIENCE,OVERSEAS_EXPLORATION", "NETWORKING,EVENT_NETWORKING", "ETC,ETC"})
    void convertsExternalAiTypeToExistingInternalCode(AiActivityType external, Competition.ActivityType internal) {
        assertThat(external.toActivityType()).isEqualTo(internal);
    }

    @Test
    void invalidActivityIsRejected() {
        assertThatThrownBy(() -> AiActivityType.INVALID_ACTIVITY.toActivityType())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("부적합");
    }
}
