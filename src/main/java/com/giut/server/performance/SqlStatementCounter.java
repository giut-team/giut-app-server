package com.giut.server.performance;

import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Slf4j
@Component
@Profile("perf")
@ConditionalOnProperty(
        prefix = "app.performance.sql-counter",
        name = "enabled",
        havingValue = "true"
)
public class SqlStatementCounter {

    private final Statistics statistics;

    public SqlStatementCounter(EntityManagerFactory entityManagerFactory) {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        this.statistics = sessionFactory.getStatistics();
        this.statistics.setStatisticsEnabled(true);
    }

    /**
     * Hibernate 통계는 애플리케이션 전체에서 공유되므로 성능 기준 측정은 한 번에 하나씩 실행한다.
     */
    public synchronized <T> MeasuredResult<T> measure(String scenario, Supplier<T> operation) {
        statistics.clear();
        long startedAt = System.nanoTime();

        T result = operation.get();

        double durationMillis = (System.nanoTime() - startedAt) / 1_000_000.0;
        SqlStatementMeasurement measurement = new SqlStatementMeasurement(
                scenario,
                statistics.getPrepareStatementCount(),
                statistics.getQueryExecutionCount(),
                durationMillis
        );
        log.info(
                "[SQL BASELINE] scenario={}, sqlStatements={}, hibernateQueries={}, durationMs={}",
                measurement.scenario(),
                measurement.sqlStatementCount(),
                measurement.hibernateQueryCount(),
                "%.3f".formatted(measurement.durationMillis())
        );
        return new MeasuredResult<>(result, measurement);
    }

    public record MeasuredResult<T>(T result, SqlStatementMeasurement measurement) {
    }
}
