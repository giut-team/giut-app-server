package com.giut.server.profile.dto.request;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicProfileSearchRequestTest {

    @Test
    void acceptsOptionalGradeWithinProfileGradeRange() {
        var request = new PublicProfileSearchRequest(0, null, null, null, null, (short) 3, null);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    @Test
    void rejectsGradeOutsideProfileGradeRange() {
        var tooLow = new PublicProfileSearchRequest(0, null, null, null, null, (short) 0, null);
        var tooHigh = new PublicProfileSearchRequest(0, null, null, null, null, (short) 6, null);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(tooLow)).anySatisfy(violation ->
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("grade"));
            assertThat(validator.validate(tooHigh)).anySatisfy(violation ->
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("grade"));
        }
    }
}
