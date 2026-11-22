package com.giut.server.competition.service;

import com.giut.server.competition.dto.request.CompetitionSearchRequest;
import com.giut.server.competition.dto.response.PublicCompetitionListResponse;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.repository.CompetitionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공개 목록 개수 캐시를 실제 JPA·H2로 확인한다. 다른 테스트는 캐시를 끄지만(test config) 이 클래스는 TTL을 켠다.
 * 캐시는 컨텍스트에 하나라 테스트 메서드끼리 키가 겹치지 않게 한 메서드에서 순서대로 확인한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_competition_list_count_cache;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF",
        "giut.competition.list-count-cache.ttl=10m"
})
class CompetitionListCountCachePersistenceTest {

    @Autowired private CompetitionService competitionService;
    @Autowired private CompetitionRepository competitions;

    @AfterEach
    void cleanUp() {
        competitions.deleteAll();
    }

    @Test
    void cachesTotalPerFilterButKeepsPageContentAndHasNextFresh() {
        publish(3, Competition.Category.WEB_MOBILE_IT);

        // 첫 요청은 세어서 캐시한다.
        assertPage(list(0, null, null), 3, 2, true, 2);

        publish(2, Competition.Category.WEB_MOBILE_IT);

        // 같은 필터는 TTL 동안 캐시된 개수(3)를 쓴다. 목록 내용과 다음 페이지 여부는 매번 새로 읽는다.
        PublicCompetitionListResponse cached = list(0, null, null);
        assertPage(cached, 3, 2, true, 2);
        assertThat(cached.competitions()).extracting(c -> c.title()).containsOnly("캐시 대상 4", "캐시 대상 5");

        // 캐시된 개수가 지금 읽은 결과보다 작으면, 읽은 결과가 보여주는 최소 개수(2 + 2 + 다음 페이지 1)로 올린다.
        assertPage(list(1, null, null), 5, 3, true, 2);

        // 마지막 페이지는 읽은 결과만으로 정확한 개수를 안다.
        assertPage(list(2, null, null), 5, 3, false, 1);

        // 검색어 조건은 캐시하지 않는다.
        assertPage(list(0, "캐시 대상", null), 5, 3, true, 2);

        // 필터 조합이 다르면 따로 센다.
        assertPage(list(0, null, Competition.Category.WEB_MOBILE_IT), 5, 3, true, 2);
    }

    private PublicCompetitionListResponse list(int page, String keyword, Competition.Category category) {
        return competitionService.getCompetitions(
                new CompetitionSearchRequest(page, 2, keyword, category, null, null, null));
    }

    private static void assertPage(PublicCompetitionListResponse response, long totalElements, int totalPages,
                                   boolean hasNext, int contentSize) {
        assertThat(response.totalElements()).isEqualTo(totalElements);
        assertThat(response.totalPages()).isEqualTo(totalPages);
        assertThat(response.hasNext()).isEqualTo(hasNext);
        assertThat(response.competitions()).hasSize(contentSize);
    }

    private void publish(int count, Competition.Category category) {
        Instant now = Instant.now();
        long existing = competitions.count();
        for (int i = 1; i <= count; i++) {
            competitions.saveAndFlush(Competition.create("캐시 대상 " + (existing + i), category, "주최", "대학생",
                    "목록 소개", now.minusSeconds(3600), now.plusSeconds(86_400),
                    Competition.PublicationStatus.PUBLISHED, Competition.VerificationStatus.UNVERIFIED, null));
        }
    }
}
