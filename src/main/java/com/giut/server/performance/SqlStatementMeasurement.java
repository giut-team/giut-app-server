package com.giut.server.performance;

public record SqlStatementMeasurement(
        String scenario,
        long sqlStatementCount,
        long hibernateQueryCount,
        double durationMillis
) {
}
