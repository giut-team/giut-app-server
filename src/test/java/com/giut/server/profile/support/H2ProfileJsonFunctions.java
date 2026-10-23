package com.giut.server.profile.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Test-only support for the existing MySQL string-array role predicate; not MySQL integration coverage. */
public final class H2ProfileJsonFunctions {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private H2ProfileJsonFunctions() {}

    public static String jsonQuote(String value) throws JsonProcessingException {
        return value == null ? null : MAPPER.writeValueAsString(value);
    }

    public static int jsonContains(String document, String candidate) throws JsonProcessingException {
        if (document == null || candidate == null) return 0;
        var array = MAPPER.readTree(document);
        var expected = MAPPER.readTree(candidate);
        if (!array.isArray()) throw new IllegalArgumentException("Test role document must be an array");
        for (var element : array) if (element.equals(expected)) return 1;
        return 0;
    }
}
