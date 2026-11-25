package com.giut.server.profile.service;

import org.springframework.boot.test.context.SpringBootTest;

/** CI에서 항상 실행되는 H2 버전. 실제 MySQL 잠금 동작은 {@link PortfolioItemConcurrencyMySqlTest}에서 확인한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_portfolio_lock;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class PortfolioItemConcurrencyPersistenceTest extends PortfolioItemConcurrencyScenarios {
}
