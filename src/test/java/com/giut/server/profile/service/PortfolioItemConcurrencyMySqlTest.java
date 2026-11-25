package com.giut.server.profile.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/** Opt-in only: this schema is disposable and must never point to a shared database. */
@EnabledIfEnvironmentVariable(named = "GIUT_PORTFOLIO_MYSQL_TEST", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:33399/giut_portfolio_lock_test?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=root", "spring.datasource.password=giut-local-test",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL="
})
class PortfolioItemConcurrencyMySqlTest extends PortfolioItemConcurrencyScenarios {
}
