package com.giut.server.competition.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.competition.entity.Competition;
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
    void listsOnlyDraftAndPublishedForEveryAllowedCaller(String caller) throws Exception {
        Competition published = competition(Competition.PublicationStatus.PUBLISHED);
        Competition draft = competition(Competition.PublicationStatus.DRAFT);
        competition(Competition.PublicationStatus.REJECTED);
        competition(Competition.PublicationStatus.ARCHIVED);
        User actor = switch (caller) {
            case "ADMIN" -> admin;
            case "VERIFIED" -> verified;
            default -> unverified;
        };

        mvc.perform(as(actor, get("/api/competitions").param("page", "0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.competitions.length()").value(2))
                .andExpect(jsonPath("$.competitions[0].id").value(draft.getId()))
                .andExpect(jsonPath("$.competitions[1].id").value(published.getId()))
                .andExpect(jsonPath("$.competitions[0].verified").isBoolean())
                .andExpect(jsonPath("$.competitions[0].verified").value(false))
                .andExpect(jsonPath("$.competitions[1].verified").isBoolean())
                .andExpect(jsonPath("$.competitions[1].verified").value(false))
                .andExpect(jsonPath("$.competitions[0].summary").value("목록 소개"))
                .andExpect(jsonPath("$.competitions[0].publicationStatus").doesNotExist())
                .andExpect(jsonPath("$.competitions[0].verificationStatus").doesNotExist());
    }

    @ParameterizedTest
    @EnumSource(Competition.VerificationStatus.class)
    void verificationStatusControlsFlagButDoesNotFilterEitherListedPublicationState(Competition.VerificationStatus verification) throws Exception {
        boolean expected = verification == Competition.VerificationStatus.AUTO_VERIFIED
                || verification == Competition.VerificationStatus.MANUALLY_VERIFIED;
        Instant now = Instant.now();
        for (Competition.PublicationStatus state : List.of(
                Competition.PublicationStatus.DRAFT, Competition.PublicationStatus.PUBLISHED)) {
            competitions.saveAndFlush(Competition.create("검증 상태와 별개", Competition.Category.WEB_MOBILE_IT,
                    "주최", "대학생", "목록 소개", now.minusSeconds(3600), now.plusSeconds(7200), state, verification, null));
        }
        mvc.perform(as(unverified, get("/api/competitions")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.competitions[0].verified").isBoolean())
                .andExpect(jsonPath("$.competitions[0].verified").value(expected))
                .andExpect(jsonPath("$.competitions[1].verified").isBoolean())
                .andExpect(jsonPath("$.competitions[1].verified").value(expected));
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
    void paginationAndCountUseBothStatesBeforePagingAndIgnoreNewerHiddenRows() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ids.add(competition(i % 2 == 0 ? Competition.PublicationStatus.DRAFT
                    : Competition.PublicationStatus.PUBLISHED).getId());
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
    void keywordCategoryAndRecruitmentFiltersRemainAndConditionsForBothStates(String status) throws Exception {
        Instant now = Instant.now();
        Instant start = "UPCOMING".equals(status) ? now.plusSeconds(3600) : now.minusSeconds(7200);
        Instant end = "CLOSED".equals(status) ? now.minusSeconds(3600) : now.plusSeconds(7200);
        Competition draft = save("개발 목록", "주최", Competition.Category.WEB_MOBILE_IT,
                Competition.PublicationStatus.DRAFT, start, end);
        Competition published = save("목록", "개발 주최", Competition.Category.WEB_MOBILE_IT,
                Competition.PublicationStatus.PUBLISHED, start, end);
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
                .andExpect(jsonPath("$.competitions[0].id").value(draft.getId()));
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
    void draftListingDoesNotExpandOtherEndpoints() throws Exception {
        Competition published = competition(Competition.PublicationStatus.PUBLISHED);
        Competition draft = competition(Competition.PublicationStatus.DRAFT);
        competition(Competition.PublicationStatus.REJECTED);
        competition(Competition.PublicationStatus.ARCHIVED);
        top5Cache.refresh();
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
    void archivingDraftRemovesItFromListWithoutDeletingRow() throws Exception {
        Competition draft = competition(Competition.PublicationStatus.DRAFT);
        mvc.perform(as(admin, delete("/api/admin/competitions/" + draft.getId()))).andExpect(status().isNoContent());
        mvc.perform(as(unverified, get("/api/competitions")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.competitions").isEmpty());
        assertThat(competitions.findById(draft.getId()).orElseThrow().getPublicationStatus())
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
