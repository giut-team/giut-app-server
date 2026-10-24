package com.giut.server.competition.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.competition.entity.Competition;
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
                .andExpect(jsonPath("$.competitions[0].summary").value("목록 소개"))
                .andExpect(jsonPath("$.competitions[0].publicationStatus").doesNotExist())
                .andExpect(jsonPath("$.competitions[0].verificationStatus").doesNotExist());
    }

    @ParameterizedTest
    @EnumSource(Competition.VerificationStatus.class)
    void verificationStatusDoesNotFilterEitherListedPublicationState(Competition.VerificationStatus verification) throws Exception {
        for (Competition.PublicationStatus state : List.of(
                Competition.PublicationStatus.DRAFT, Competition.PublicationStatus.PUBLISHED)) {
            competitions.saveAndFlush(Competition.create("검증 상태와 별개", Competition.Category.WEB_MOBILE_IT,
                    "주최", "대학생", "목록 소개", null, null, state, verification, null));
        }
        mvc.perform(as(unverified, get("/api/competitions")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
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
    @ValueSource(strings = {"UPCOMING", "OPEN", "CLOSED"})
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
    void draftListingDoesNotExpandOtherEndpoints() throws Exception {
        Competition published = competition(Competition.PublicationStatus.PUBLISHED);
        Competition draft = competition(Competition.PublicationStatus.DRAFT);
        competition(Competition.PublicationStatus.REJECTED);
        competition(Competition.PublicationStatus.ARCHIVED);
        for (String endpoint : List.of("/api/competitions/top5", "/api/competitions/closing-soon")) {
            mvc.perform(as(unverified, get(endpoint))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(published.getId()));
        }
        mvc.perform(as(unverified, get("/api/competitions/" + draft.getId())))
                .andExpect(status().isNotFound());
        mvc.perform(as(verified, post("/api/competitions/" + draft.getId() + "/scrap")))
                .andExpect(status().isNotFound());
        mvc.perform(as(verified, delete("/api/competitions/" + draft.getId() + "/scrap")))
                .andExpect(status().isNotFound());
        assertThat(scraps.count()).isZero();
        assertThat(competitions.findById(draft.getId()).orElseThrow().getViewCount()).isZero();
        mvc.perform(as(verified, get("/api/competitions/" + published.getId()))).andExpect(status().isOk());
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
