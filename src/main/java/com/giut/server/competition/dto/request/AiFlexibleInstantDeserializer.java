package com.giut.server.competition.dto.request;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Accepts either a full ISO-8601 instant ("2026-11-01T00:00:00Z") or a bare date
 * ("2026-11-01"), normalizing the bare date to the start or end of that day in Asia/Seoul.
 */
public abstract class AiFlexibleInstantDeserializer extends StdDeserializer<Instant> {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    protected AiFlexibleInstantDeserializer() {
        super(Instant.class);
    }

    protected abstract LocalTime timeOfDay();

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) {
        String value = parser.getText();
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.indexOf('T') >= 0) {
            return Instant.parse(value);
        }
        return LocalDate.parse(value).atTime(timeOfDay()).atZone(ZONE).toInstant();
    }

    public static class Start extends AiFlexibleInstantDeserializer {
        @Override
        protected LocalTime timeOfDay() {
            return LocalTime.MIDNIGHT;
        }
    }

    public static class End extends AiFlexibleInstantDeserializer {
        @Override
        protected LocalTime timeOfDay() {
            return LocalTime.of(23, 59, 59);
        }
    }
}
