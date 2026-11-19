package com.giut.server.competition.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.entity.CompetitionScrap;
import com.giut.server.competition.dto.request.CompetitionPageRequest;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.competition.repository.CompetitionScrapRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC, security and JPA against this class's disposable H2 database only. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_competition_list;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
class CompetitionListVisibilityPersistenceTest {
    @Autowired private WebApplicationContext context;
    @Autowired private JwtProvider jwt;
    @Autowired private UserRepository users;
    @Autowired private CompetitionRepository competitions;
    @Autowired private CompetitionTop5Cache top5Cache;
    @Autowired private CompetitionService competitionService;
    @Autowired private CompetitionScrapRepository scraps;
    @Autowired private org.springframework.transaction.support.TransactionTemplate transactions;
    private MockMvc mvc;
    private User admin;
    private User verified;
    private User unverified;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin = users.saveAndFlush(User.createAdmin("list-admin@example.com", "hash", "관리자"));
        verified = User.createOAuthUser("list-verified@example.com", "인증", User.OAuthProvider.KAKAO, "list-verified");
        verified.verifyUniversityEmail("list-verified@uos.ac.kr");
        verified = users.saveAndFlush(verified);
        unverified = users.saveAndFlush(User.createOAuthUser(
                "list-unverified@example.com", "미인증", User.OAuthProvider.KAKAO, "list-unverified"));
    }

    @AfterEach
    void cleanUp() {
        // No shared DB or other test class's data. HTTP transactions have already committed.
        scraps.deleteAll();
        competitions.deleteAll();
        users.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "VERIFIED", "UNVERIFIED"})
    void listsOnlyPublishedForEveryAllowedCaller(String caller) throws Exception {
        Competition published = competition(Competition.PublicationStatus.PUBLISHED);
        competition(Competition.PublicationStatus.DRAFT);
        competition(Competition.PublicationStatus.REJECTED);
        competition(Competition.PublicationStatus.ARCHIVED);
        User actor = switch (caller) {
            case "ADMIN" -> admin;
            case "VERIFIED" -> verified;
            default -> unverified;
        };

        mvc.perform(as(actor, get("/api/competitions").param("page", "0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.competitions.length()").value(1))
                .andExpect(jsonPath("$.competitions[0].id").value(published.getId()))
                .andExpect(jsonPath("$.competitions[0].verified").isBoolean())
                .andExpect(jsonPath("$.competitions[0].verified").value(false))
                .andExpect(jsonPath("$.competitions[0].summary").value("목록 소개"))
                .andExpect(jsonPath("$.competitions[0].publicationStatus").doesNotExist())
                .andExpect(jsonPath("$.competitions[0].verificationStatus").doesNotExist());
    }

    @ParameterizedTest
    @EnumSource(Competition.VerificationStatus.class)
    void verificationStatusControlsFlagButDoesNotFilterPublishedList(Competition.VerificationStatus verification) throws Exception {
        boolean expected = verification == Competition.VerificationStatus.AUTO_VERIFIED
                || verification == Competition.VerificationStatus.MANUALLY_VERIFIED;
        Instant now = Instant.now();
        for (Competition.PublicationStatus state : List.of(
                Competition.PublicationStatus.DRAFT, Competition.PublicationStatus.PUBLISHED)) {
            competitions.saveAndFlush(Competition.create("검증 상태와 별개", Competition.Category.WEB_MOBILE_IT,
                    "주최", "대학생", "목록 소개", now.minusSeconds(3600), now.plusSeconds(7200), state, verification, null));
        }
        mvc.perform(as(unverified, get("/api/competitions")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.competitions[0].verified").isBoolean())
                .andExpect(jsonPath("$.competitions[0].verified").value(expected));
        Competition published = competitions.findAll().stream()
                .filter(c -> c.getPublicationStatus() == Competition.PublicationStatus.PUBLISHED).findFirst().orElseThrow();
        Competition draft = competitions.findAll().stream()
                .filter(c -> c.getPublicationStatus() == Competition.PublicationStatus.DRAFT).findFirst().orElseThrow();
        top5Cache.refresh();
        for (String endpoint : List.of("/api/competitions/top5", "/api/competitions/closing-soon")) {
            String listPath = endpoint.endsWith("closing-soon") ? "$.competitions" : "$";
            mvc.perform(as(unverified, get(endpoint))).andExpect(status().isOk())
                    .andExpect(jsonPath(listPath + ".length()").value(1))
                    .andExpect(jsonPath(listPath + "[0].id").value(published.getId()))
                    .andExpect(jsonPath(listPath + "[0].verified").isBoolean())
                    .andExpect(jsonPath(listPath + "[0].verified").value(expected));
        }
        mvc.perform(as(unverified, get("/api/competitions/" + published.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verified").isBoolean())
                .andExpect(jsonPath("$.verified").value(expected));
        mvc.perform(as(unverified, get("/api/competitions/" + draft.getId())))
                .andExpect(status().isNotFound());
        assertThat(competitions.findAll()).hasSize(2).allSatisfy(c ->
                assertThat(c.getVerificationStatus()).isEqualTo(verification));
    }

    @Test
    void keywordTreatsLikeMetacharactersAsLiteralInTitleAndHost() throws Exception {
        for (String keyword : List.of("100%", "A_B", "느낌!", "경로\\파일")) {
            Competition title = save("제목 " + keyword, "주최", Competition.Category.ETC,
                    Competition.PublicationStatus.PUBLISHED, null, Instant.now().plusSeconds(3600));
            save("숨김 " + keyword, "주최", Competition.Category.ETC,
                    Competition.PublicationStatus.DRAFT, null, Instant.now().plusSeconds(3600));
            Competition host = save("일반 제목", "기관 " + keyword, Competition.Category.ETC,
                    Competition.PublicationStatus.PUBLISHED, null, Instant.now().plusSeconds(3600));
            save("제목 1000 A0B 느낌표 경로파일", "일반 기관", Competition.Category.ETC,
                    Competition.PublicationStatus.PUBLISHED, null, Instant.now().plusSeconds(3600));

            mvc.perform(as(verified, get("/api/competitions").param("keyword", keyword)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.competitions[0].id").value(host.getId()))
                    .andExpect(jsonPath("$.competitions[1].id").value(title.getId()));
            competitions.deleteAll();
        }
    }

    @Test
    void paginationAndCountUsePublishedOnlyBeforePagingAndIgnoreNewerHiddenRows() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ids.add(competition(Competition.PublicationStatus.PUBLISHED).getId());
            competition(Competition.PublicationStatus.DRAFT);
            competition(Competition.PublicationStatus.ARCHIVED);
            competition(Competition.PublicationStatus.REJECTED);
        }
        Collections.reverse(ids);
        var first = mvc.perform(as(verified, get("/api/competitions")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.competitions.length()").value(10));
        for (int i = 0; i < 10; i++) {
            first.andExpect(jsonPath("$.competitions[" + i + "].id").value(ids.get(i)));
        }
        mvc.perform(as(verified, get("/api/competitions").param("page", "1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(12)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(false)).andExpect(jsonPath("$.competitions.length()").value(2))
                .andExpect(jsonPath("$.competitions[0].id").value(ids.get(10)))
                .andExpect(jsonPath("$.competitions[1].id").value(ids.get(11)));
        mvc.perform(as(verified, get("/api/competitions").param("page", "2")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.competitions").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPCOMING", "OPEN"})
    void keywordCategoryAndRecruitmentFiltersRemainAndConditionsForPublishedList(String status) throws Exception {
        Instant now = Instant.now();
        Instant start = "UPCOMING".equals(status) ? now.plusSeconds(3600) : now.minusSeconds(7200);
        Instant end = "CLOSED".equals(status) ? now.minusSeconds(3600) : now.plusSeconds(7200);
        Competition olderPublished = save("개발 목록", "주최", Competition.Category.WEB_MOBILE_IT,
                Competition.PublicationStatus.PUBLISHED, start, end);
        Competition published = save("목록", "개발 주최", Competition.Category.WEB_MOBILE_IT,
                Competition.PublicationStatus.PUBLISHED, start, end);
        save("개발 목록", "주최", Competition.Category.WEB_MOBILE_IT, Competition.PublicationStatus.DRAFT, start, end);
        for (Competition.PublicationStatus state : Competition.PublicationStatus.values()) {
            save("개발 목록", "주최", Competition.Category.ETC, state, start, end); // Wrong category
            save("다른 목록", "주최", Competition.Category.WEB_MOBILE_IT, state, start, end); // Wrong keyword
            save("개발 목록", "주최", Competition.Category.WEB_MOBILE_IT, state,
                    "CLOSED".equals(status) ? now.plusSeconds(3600) : now.minusSeconds(7200),
                    "CLOSED".equals(status) ? now.plusSeconds(7200) : now.minusSeconds(3600)); // Wrong recruitment
        }
        save("개발 목록", "주최", Competition.Category.WEB_MOBILE_IT, Competition.PublicationStatus.REJECTED, start, end);
        save("개발 목록", "주최", Competition.Category.WEB_MOBILE_IT, Competition.PublicationStatus.ARCHIVED, start, end);

        mvc.perform(as(admin, get("/api/competitions").param("keyword", " 개발 ")
                        .param("category", "WEB_MOBILE_IT").param("status", status).param("size", "1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.competitions.length()").value(1))
                .andExpect(jsonPath("$.competitions[0].id").value(published.getId()))
                .andExpect(jsonPath("$.competitions[0].recruitmentStatus").value(status));
        mvc.perform(as(admin, get("/api/competitions").param("keyword", "개발")
                        .param("category", "WEB_MOBILE_IT").param("status", status).param("size", "1").param("page", "1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.competitions[0].id").value(olderPublished.getId()));
    }

    @Test
    void closingSoonKeepsTimeBoundariesAndDeadlineOrder() {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        Competition later = save("7일 경계", "주최", Competition.Category.ETC, Competition.PublicationStatus.PUBLISHED,
                now.minusSeconds(60), now.plusSeconds(7 * 86400));
        Competition earlier = save("마감 임박", "주최", Competition.Category.ETC, Competition.PublicationStatus.PUBLISHED,
                null, now.plusSeconds(1));
        save("현재 마감", "주최", Competition.Category.ETC, Competition.PublicationStatus.PUBLISHED,
                null, now);
        save("7일 초과", "주최", Competition.Category.ETC, Competition.PublicationStatus.PUBLISHED,
                null, now.plusSeconds(7 * 86400 + 1));
        try (var time = org.mockito.Mockito.mockStatic(Instant.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(now);
            var page = competitionService.getClosingSoonCompetitions(new CompetitionPageRequest(0, 1, null));
            assertThat(page.totalElements()).isEqualTo(2);
            assertThat(page.hasNext()).isTrue();
            assertThat(page.competitions()).extracting("id").containsExactly(earlier.getId());
            var next = competitionService.getClosingSoonCompetitions(new CompetitionPageRequest(1, 1, null));
            assertThat(next.competitions()).extracting("id").containsExactly(later.getId());
        }
    }

    @Test
    void closingSoonReturnsEmptyPageWhenNothingMatches() throws Exception {
        mvc.perform(as(unverified, get("/api/competitions/closing-soon")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.competitions").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void closingSoonPaginatesEligibleCompetitionsInDeadlineAndIdOrder() throws Exception {
        Instant now = Instant.now();
        List<Competition> eligible = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            eligible.add(save("마감 임박", "주최", Competition.Category.ETC,
                    Competition.PublicationStatus.PUBLISHED, now.minusSeconds(60), now.plusSeconds(3600)));
        }
        Collections.reverse(eligible);
        save("늦은 마감", "주최", Competition.Category.ETC, Competition.PublicationStatus.PUBLISHED,
                now.minusSeconds(60), now.plusSeconds(86400 * 8));
        save("모집 예정", "주최", Competition.Category.ETC, Competition.PublicationStatus.PUBLISHED,
                now.plusSeconds(3600), now.plusSeconds(7200));
        save("마감됨", "주최", Competition.Category.ETC, Competition.PublicationStatus.PUBLISHED,
                now.minusSeconds(7200), now.minusSeconds(60));
        save("마감일 없음", "주최", Competition.Category.ETC, Competition.PublicationStatus.PUBLISHED,
                now.minusSeconds(60), null);
        save("초안", "주최", Competition.Category.ETC, Competition.PublicationStatus.DRAFT,
                now.minusSeconds(60), now.plusSeconds(3600));

        mvc.perform(as(unverified, get("/api/competitions/closing-soon")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10)).andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.competitions.length()").value(10))
                .andExpect(jsonPath("$.competitions[0].id").value(eligible.get(0).getId()))
                .andExpect(jsonPath("$.competitions[9].id").value(eligible.get(9).getId()));
        mvc.perform(as(unverified, get("/api/competitions/closing-soon").param("page", "1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(12)).andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.competitions.length()").value(2))
                .andExpect(jsonPath("$.competitions[0].id").value(eligible.get(10).getId()));
        mvc.perform(as(unverified, get("/api/competitions/closing-soon").param("page", "2")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.competitions").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(12));
        mvc.perform(as(unverified, get("/api/competitions/closing-soon").param("size", "3")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.competitions.length()").value(3));
    }

    @Test
    void closingSoonFiltersCategoryBeforePaginationAndCounting() throws Exception {
        Instant now = Instant.now();
        Competition first = save("개발 1", "주최", Competition.Category.WEB_MOBILE_IT,
                Competition.PublicationStatus.PUBLISHED, now.minusSeconds(60), now.plusSeconds(7200));
        save("기타", "주최", Competition.Category.ETC, Competition.PublicationStatus.PUBLISHED,
                now.minusSeconds(60), now.plusSeconds(3600));
        Competition second = save("개발 2", "주최", Competition.Category.WEB_MOBILE_IT,
                Competition.PublicationStatus.PUBLISHED, now.minusSeconds(60), now.plusSeconds(10800));
        mvc.perform(as(unverified, get("/api/competitions/closing-soon").param("category", "WEB_MOBILE_IT").param("size", "1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.competitions[0].id").value(first.getId()));
        mvc.perform(as(unverified, get("/api/competitions/closing-soon").param("category", "WEB_MOBILE_IT").param("size", "1").param("page", "1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.competitions[0].id").value(second.getId()));
    }

    @Test
    void closingSoonRejectsInvalidPagination() throws Exception {
        for (String[] param : List.of(new String[]{"page", "-1"}, new String[]{"page", "abc"},
                new String[]{"size", "0"}, new String[]{"size", "21"}, new String[]{"size", "abc"},
                new String[]{"page", "2147483647"}, new String[]{"category", "INVALID"})) {
            mvc.perform(as(unverified, get("/api/competitions/closing-soon").param(param[0], param[1])))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void top5FillsArchivedAndExpiredSpotsFromTenRankedCandidates() throws Exception {
        List<Competition> ranked = new ArrayList<>();
        for (int i = 0; i < 11; i++) ranked.add(competition(Competition.PublicationStatus.PUBLISHED));
        Collections.reverse(ranked); // Equal scores are ordered by id descending.
        assertThat(competitions.findTop10ByPopularity(Competition.PublicationStatus.PUBLISHED.name(), Instant.now()))
                .extracting(Competition::getId).containsExactlyElementsOf(
                        ranked.subList(0, 10).stream().map(Competition::getId).toList());
        top5Cache.refresh();
        mvc.perform(as(verified, get("/api/competitions/top5"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].id").value(ranked.get(0).getId()))
                .andExpect(jsonPath("$[4].id").value(ranked.get(4).getId()));
        mvc.perform(as(admin, delete("/api/admin/competitions/" + ranked.get(0).getId())))
                .andExpect(status().isNoContent());
        Competition expired = ranked.get(1);
        org.springframework.test.util.ReflectionTestUtils.setField(expired, "applicationEndAt", Instant.now().minusSeconds(10));
        competitions.saveAndFlush(expired);
        mvc.perform(as(verified, get("/api/competitions/top5"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].id").value(ranked.get(2).getId()))
                .andExpect(jsonPath("$[4].id").value(ranked.get(6).getId()));
        assertThat(competitions.findTop10ByPopularity(Competition.PublicationStatus.PUBLISHED.name(), Instant.now()))
                .hasSize(9);
    }

    @Test
    void top5DeadlineBoundaryUsesStrictlyLaterThanNow() {
        Instant now = Instant.parse("2090-01-01T00:00:00Z");
        Competition before = save("이전", "주최", Competition.Category.ETC, Competition.PublicationStatus.PUBLISHED,
                null, now.minusSeconds(1));
        Competition at = save("동일", "주최", Competition.Category.ETC, Competition.PublicationStatus.PUBLISHED,
                null, now);
        Competition after = save("이후", "주최", Competition.Category.ETC, Competition.PublicationStatus.PUBLISHED,
                null, now.plusSeconds(1));
        Competition noDeadline = save("없음", "주최", Competition.Category.ETC, Competition.PublicationStatus.PUBLISHED,
                null, null);
        assertThat(competitions.findPublishedIds(List.of(before.getId(), at.getId(), after.getId(), noDeadline.getId()),
                Competition.PublicationStatus.PUBLISHED, now)).containsExactly(after.getId());
        assertThat(competitions.findTop10ByPopularity(Competition.PublicationStatus.PUBLISHED.name(), now))
                .extracting(Competition::getId).containsExactly(after.getId());
    }

    @Test
    void hidesExpiredAndMissingDeadlinesFromListsButKeepsPublishedDetails() throws Exception {
        Instant now = Instant.now();
        Competition active = save("진행", "주최", Competition.Category.WEB_MOBILE_IT,
                Competition.PublicationStatus.PUBLISHED, now.minusSeconds(3600), now.plusSeconds(7200));
        Competition expired = save("종료", "주최", Competition.Category.WEB_MOBILE_IT,
                Competition.PublicationStatus.PUBLISHED, now.minusSeconds(7200), now.minusSeconds(3600));
        Competition missing = save("마감 미정", "주최", Competition.Category.WEB_MOBILE_IT,
                Competition.PublicationStatus.PUBLISHED, now.minusSeconds(7200), null);
        top5Cache.refresh();
        mvc.perform(as(verified, get("/api/competitions"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.competitions[0].id").value(active.getId()));
        mvc.perform(as(verified, get("/api/competitions").param("status", "CLOSED")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(as(verified, get("/api/competitions/top5"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(active.getId()));
        mvc.perform(as(verified, get("/api/competitions/" + expired.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recruitmentStatus").value("CLOSED"));
        mvc.perform(as(verified, get("/api/competitions/" + missing.getId()))).andExpect(status().isOk());

        org.springframework.test.util.ReflectionTestUtils.setField(active, "applicationEndAt", now.minusSeconds(1));
        competitions.saveAndFlush(active);
        mvc.perform(as(verified, get("/api/competitions/top5")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void draftIsHiddenFromEveryPublicEndpoint() throws Exception {
        Competition published = competition(Competition.PublicationStatus.PUBLISHED);
        Competition draft = competition(Competition.PublicationStatus.DRAFT);
        competition(Competition.PublicationStatus.REJECTED);
        competition(Competition.PublicationStatus.ARCHIVED);
        top5Cache.refresh();
        mvc.perform(as(unverified, get("/api/competitions"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.competitions[0].id").value(published.getId()));
        for (String endpoint : List.of("/api/competitions/top5", "/api/competitions/closing-soon")) {
            String listPath = endpoint.endsWith("closing-soon") ? "$.competitions" : "$";
            mvc.perform(as(unverified, get(endpoint))).andExpect(status().isOk())
                    .andExpect(jsonPath(listPath + ".length()").value(1))
                    .andExpect(jsonPath(listPath + "[0].id").value(published.getId()))
                    .andExpect(jsonPath(listPath + "[0].verified").value(false));
        }
        mvc.perform(as(unverified, get("/api/competitions/" + draft.getId())))
                .andExpect(status().isNotFound());
        mvc.perform(as(verified, post("/api/competitions/" + draft.getId() + "/scrap")))
                .andExpect(status().isNotFound());
        mvc.perform(as(verified, delete("/api/competitions/" + draft.getId() + "/scrap")))
                .andExpect(status().isNotFound());
        assertThat(scraps.count()).isZero();
        assertThat(competitions.findById(draft.getId()).orElseThrow().getViewCount()).isZero();
        mvc.perform(as(verified, get("/api/competitions/" + published.getId()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false));
        mvc.perform(as(verified, post("/api/competitions/" + published.getId() + "/scrap")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scrapped").value(true));
        mvc.perform(as(unverified, post("/api/competitions/" + published.getId() + "/scrap")))
                .andExpect(status().isForbidden());
    }

    @Test
    void archivingPublishedRemovesItFromListWithoutDeletingRow() throws Exception {
        Competition published = competition(Competition.PublicationStatus.PUBLISHED);
        mvc.perform(as(unverified, get("/api/competitions")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(as(admin, delete("/api/admin/competitions/" + published.getId()))).andExpect(status().isNoContent());
        mvc.perform(as(unverified, get("/api/competitions")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.competitions").isEmpty());
        assertThat(competitions.findById(published.getId()).orElseThrow().getPublicationStatus())
                .isEqualTo(Competition.PublicationStatus.ARCHIVED);
    }

    @Test
    void anonymousAccessAndInvalidQueryParametersAreStillRejected() throws Exception {
        competition(Competition.PublicationStatus.DRAFT);
        mvc.perform(get("/api/competitions")).andExpect(status().isUnauthorized());
        for (String[] param : List.of(new String[]{"page", "-1"}, new String[]{"size", "0"},
                new String[]{"size", "21"}, new String[]{"category", "INVALID"}, new String[]{"status", "DRAFT"})) {
            mvc.perform(as(admin, get("/api/competitions").param(param[0], param[1])))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void sortParameterOrdersByDeadlineLatestViewsOrScrapsWithLatestAsTieBreaker() throws Exception {
        Instant now = Instant.now();
        Competition oldestLateDeadline = save("오래됨-늦은마감", "주최", Competition.Category.WEB_MOBILE_IT,
                Competition.PublicationStatus.PUBLISHED, now.minusSeconds(3600), now.plusSeconds(30 * 86400));
        Competition middleEarlyDeadline = save("중간-빠른마감", "주최", Competition.Category.WEB_MOBILE_IT,
                Competition.PublicationStatus.PUBLISHED, now.minusSeconds(3600), now.plusSeconds(86400));
        Competition newestMiddleDeadline = save("최신-중간마감", "주최", Competition.Category.WEB_MOBILE_IT,
                Competition.PublicationStatus.PUBLISHED, now.minusSeconds(3600), now.plusSeconds(7 * 86400));
        Competition newestTieDeadline = save("최신-빠른마감-동률", "주최", Competition.Category.WEB_MOBILE_IT,
                Competition.PublicationStatus.PUBLISHED, now.minusSeconds(3600), middleEarlyDeadline.getApplicationEndAt());
        Competition hiddenDraft = competition(Competition.PublicationStatus.DRAFT); // 비공개는 어떤 정렬에도 나오지 않는다.
        transactions.executeWithoutResult(status -> {
            for (int i = 0; i < 5; i++) competitions.incrementViewCount(oldestLateDeadline.getId(), Competition.PublicationStatus.PUBLISHED);
            for (int i = 0; i < 2; i++) competitions.incrementViewCount(newestMiddleDeadline.getId(), Competition.PublicationStatus.PUBLISHED);
            for (int i = 0; i < 2; i++) competitions.incrementViewCount(middleEarlyDeadline.getId(), Competition.PublicationStatus.PUBLISHED);
        });

        // 기본(파라미터 없음)과 LATEST는 기존과 같은 등록 최신순이다.
        for (String sort : new String[]{null, "LATEST"}) {
            var request = get("/api/competitions");
            if (sort != null) request = request.param("sort", sort);
            mvc.perform(as(verified, request)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(4))
                    .andExpect(jsonPath("$.competitions[*].id").value(org.hamcrest.Matchers.contains(
                            newestTieDeadline.getId().intValue(), newestMiddleDeadline.getId().intValue(),
                            middleEarlyDeadline.getId().intValue(), oldestLateDeadline.getId().intValue())));
        }
        // 마감 임박순. 같은 마감이면 최신 등록이 먼저다.
        mvc.perform(as(verified, get("/api/competitions").param("sort", "DEADLINE"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.competitions[*].id").value(org.hamcrest.Matchers.contains(
                        newestTieDeadline.getId().intValue(), middleEarlyDeadline.getId().intValue(),
                        newestMiddleDeadline.getId().intValue(), oldestLateDeadline.getId().intValue())));
        // 조회수 순. 같은 조회수면 최신 등록이 먼저다.
        mvc.perform(as(verified, get("/api/competitions").param("sort", "VIEWS"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.competitions[*].id").value(org.hamcrest.Matchers.contains(
                        oldestLateDeadline.getId().intValue(), newestMiddleDeadline.getId().intValue(),
                        middleEarlyDeadline.getId().intValue(), newestTieDeadline.getId().intValue())));
        // 정렬은 페이지를 나누기 전에 적용된다.
        mvc.perform(as(verified, get("/api/competitions").param("sort", "VIEWS").param("size", "2").param("page", "1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.competitions[*].id").value(org.hamcrest.Matchers.contains(
                        middleEarlyDeadline.getId().intValue(), newestTieDeadline.getId().intValue())));
        // 스크랩 순. 스크랩 추가 API가 함께 갱신하는 scrap_count로 정렬한다. 같은 수면 최신 등록이 먼저다.
        competitionService.scrapCompetition(verified.getId(), newestTieDeadline.getId());
        competitionService.scrapCompetition(admin.getId(), newestTieDeadline.getId());
        competitionService.scrapCompetition(verified.getId(), middleEarlyDeadline.getId());
        competitionService.scrapCompetition(unverified.getId(), middleEarlyDeadline.getId());
        competitionService.scrapCompetition(admin.getId(), newestMiddleDeadline.getId());
        // 비공개 공모전은 스크랩 API로 스크랩할 수 없으므로 행만 직접 넣는다(목록에는 나오지 않아야 한다).
        scraps.saveAndFlush(CompetitionScrap.create(verified, hiddenDraft));
        scraps.saveAndFlush(CompetitionScrap.create(admin, hiddenDraft));
        scraps.saveAndFlush(CompetitionScrap.create(unverified, hiddenDraft));
        mvc.perform(as(verified, get("/api/competitions").param("sort", "SCRAPS"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.competitions[*].id").value(org.hamcrest.Matchers.contains(
                        newestTieDeadline.getId().intValue(), middleEarlyDeadline.getId().intValue(),
                        newestMiddleDeadline.getId().intValue(), oldestLateDeadline.getId().intValue())))
                .andExpect(jsonPath("$.competitions[*].scrapCount").value(org.hamcrest.Matchers.contains(2, 2, 1, 0)));
        // 필터·페이지와 함께 써도 count는 정렬과 무관하게 맞다.
        mvc.perform(as(verified, get("/api/competitions").param("sort", "SCRAPS").param("size", "3").param("page", "1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.competitions[*].id").value(org.hamcrest.Matchers.contains(
                        oldestLateDeadline.getId().intValue())));
        mvc.perform(as(verified, get("/api/competitions").param("sort", "SCRAPS").param("keyword", "빠른마감")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.competitions[*].id").value(org.hamcrest.Matchers.contains(
                        newestTieDeadline.getId().intValue(), middleEarlyDeadline.getId().intValue())));
        for (String invalid : new String[]{"POPULAR", "OLDEST"}) {
            mvc.perform(as(verified, get("/api/competitions").param("sort", invalid))).andExpect(status().isBadRequest());
        }
    }

    @Test
    void scrapCountColumnFollowsActualScrapRowsAndIsUsedForScrapSort() throws Exception {
        Competition first = competition(Competition.PublicationStatus.PUBLISHED);
        Competition second = competition(Competition.PublicationStatus.PUBLISHED);

        competitionService.scrapCompetition(verified.getId(), first.getId());
        competitionService.scrapCompetition(verified.getId(), first.getId()); // 중복 스크랩은 수를 늘리지 않는다.
        competitionService.scrapCompetition(admin.getId(), first.getId());
        competitionService.removeCompetitionScrap(unverified.getId(), first.getId()); // 없는 스크랩 취소는 줄이지 않는다.
        assertThat(competitions.findById(first.getId()).orElseThrow().getScrapCount()).isEqualTo(2);
        assertThat(scraps.countByCompetition_Id(first.getId())).isEqualTo(2);

        competitionService.scrapCompetition(unverified.getId(), second.getId());
        competitionService.removeCompetitionScrap(verified.getId(), first.getId());
        competitionService.removeCompetitionScrap(verified.getId(), first.getId());
        assertThat(competitions.findById(first.getId()).orElseThrow().getScrapCount()).isEqualTo(1);
        assertThat(competitions.findById(second.getId()).orElseThrow().getScrapCount()).isEqualTo(1);
        mvc.perform(as(verified, get("/api/competitions").param("sort", "SCRAPS"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.competitions[*].id").value(org.hamcrest.Matchers.contains(
                        second.getId().intValue(), first.getId().intValue())))
                .andExpect(jsonPath("$.competitions[*].scrapCount").value(org.hamcrest.Matchers.contains(1, 1)));

        // HTTP 경로도 같은 트랜잭션에서 카운터를 갱신한다.
        mvc.perform(as(admin, post("/api/competitions/" + second.getId() + "/scrap"))).andExpect(status().isOk());
        assertThat(competitions.findById(second.getId()).orElseThrow().getScrapCount()).isEqualTo(2);
        mvc.perform(as(admin, delete("/api/competitions/" + second.getId() + "/scrap"))).andExpect(status().isOk());
        assertThat(competitions.findById(second.getId()).orElseThrow().getScrapCount()).isEqualTo(1);
    }

    private Competition competition(Competition.PublicationStatus state) {
        return save("목록 공모전", "주최", Competition.Category.WEB_MOBILE_IT, state,
                Instant.now().minusSeconds(3600), Instant.now().plusSeconds(7200));
    }

    private Competition save(String title, String host, Competition.Category category,
                             Competition.PublicationStatus state, Instant start, Instant end) {
        return competitions.saveAndFlush(Competition.create(title, category, host, "대학생", "목록 소개",
                start, end, state, Competition.VerificationStatus.UNVERIFIED, null));
    }

    private MockHttpServletRequestBuilder as(User actor, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwt.generateAccessToken(actor)).with(servletRequest -> {
            servletRequest.setServletPath(servletRequest.getRequestURI());
            return servletRequest;
        });
    }
}
