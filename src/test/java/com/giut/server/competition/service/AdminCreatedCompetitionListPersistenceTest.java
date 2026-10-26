package com.giut.server.competition.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.competition.dto.request.CompetitionUrlRequest;
import com.giut.server.competition.dto.request.CreateCompetitionRequest;
import com.giut.server.competition.dto.request.UpsertCompetitionRequest;
import com.giut.server.competition.dto.response.AdminCompetitionResponse;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.entity.CompetitionUrl;
import com.giut.server.competition.entity.CompetitionVerificationLog;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.competition.repository.CompetitionUrlRepository;
import com.giut.server.competition.repository.CompetitionVerificationLogRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Requests use real security and service transactions; cleanup targets this disposable H2 DB only. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_admin_created_list;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
class AdminCreatedCompetitionListPersistenceTest {
    private static final String PATH = "/api/admin/competitions";
    @Autowired private WebApplicationContext context;
    @Autowired private JwtProvider jwt;
    @Autowired private UserRepository users;
    @Autowired private CompetitionRepository competitions;
    @Autowired private CompetitionUrlRepository urls;
    @Autowired private CompetitionVerificationLogRepository logs;
    @Autowired private CompetitionService service;
    @Autowired private CompetitionSeedService seeds;
    private MockMvc mvc;
    private User admin;
    private User otherAdmin;
    private User verified;
    private User unverified;
    private int urlSequence;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin = users.saveAndFlush(User.createAdmin("created-admin@example.com", "hash", "관리자"));
        otherAdmin = users.saveAndFlush(User.createAdmin("created-other@example.com", "hash", "다른 관리자"));
        verified = User.createOAuthUser("created-student@example.com", "학생", User.OAuthProvider.KAKAO, "created-student");
        verified.verifyUniversityEmail("created-student@uos.ac.kr");
        verified = users.saveAndFlush(verified);
        unverified = users.saveAndFlush(User.createOAuthUser("created-unverified@example.com", "미인증",
                User.OAuthProvider.KAKAO, "created-unverified"));
    }

    @AfterEach
    void cleanUp() {
        logs.deleteAll();
        urls.deleteAll();
        competitions.deleteAll();
        users.deleteAll();
    }

    @Test
    void creationPostIsImmediatelyListedWithAdminFieldsAndUrlsWithoutIncrementingViews() throws Exception {
        mvc.perform(as(admin, post(PATH)).contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"관리자 생성", "category":"WEB_MOBILE_IT", "summary":"소개",
                 "targetParticipants":"대학생", "publicationStatus":"DRAFT",
                 "urls":[{"type":"RECRUITMENT","url":"https://example.com/created-post","primary":true}]}
                """))
                .andExpect(status().isCreated());
        Competition created = competitions.findAll().getFirst();
        mvc.perform(as(admin, get(PATH)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.competitions[0].id").value(created.getId()))
                .andExpect(jsonPath("$.competitions[0].publicationStatus").value("DRAFT"))
                .andExpect(jsonPath("$.competitions[0].verificationStatus").value("MANUALLY_VERIFIED"))
                .andExpect(jsonPath("$.competitions[0].verifiedAt").isNotEmpty())
                .andExpect(jsonPath("$.competitions[0].targetParticipants").value("대학생"))
                .andExpect(jsonPath("$.competitions[0].urls[0].url").value("https://example.com/created-post"));
        assertThat(competitions.findById(created.getId()).orElseThrow().getViewCount()).isZero();
        mvc.perform(as(verified, get("/api/competitions")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(as(verified, get("/api/competitions/" + created.getId()))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @EnumSource(Competition.VerificationStatus.class)
    void retiredVerificationInputCannotOverrideServerAssignedStatusOrCreationLog(Competition.VerificationStatus state) throws Exception {
        mvc.perform(as(admin, post(PATH)).contentType(MediaType.APPLICATION_JSON)
                        .content(creationBody("DRAFT", "\"" + state.name() + "\"")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.verificationStatus").value("MANUALLY_VERIFIED"))
                .andExpect(jsonPath("$.verifiedAt").isNotEmpty());

        Competition stored = competitions.findAll().getFirst();
        assertThat(stored.getVerificationStatus()).isEqualTo(Competition.VerificationStatus.MANUALLY_VERIFIED);
        assertThat(stored.getVerifiedAt()).isNotNull();
        assertThat(stored.getVerifiedByUser().getId()).isEqualTo(admin.getId());
        CompetitionVerificationLog log = logs.findAll().getFirst();
        assertThat(log.getFromStatus()).isNull();
        assertThat(log.getToStatus()).isEqualTo(Competition.VerificationStatus.MANUALLY_VERIFIED);
        assertThat(log.getVerificationMethod()).isEqualTo(CompetitionVerificationLog.Method.ADMIN_REVIEW);
        assertThat(log.getActorUser().getId()).isEqualTo(admin.getId());
        assertThat(log.getReason()).isEqualTo(CompetitionVerificationLog.ADMIN_CREATION_REASON);
        mvc.perform(as(otherAdmin, get(PATH)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.competitions[0].id").value(stored.getId()))
                .andExpect(jsonPath("$.competitions[0].verificationStatus").value("MANUALLY_VERIFIED"));
        mvc.perform(as(unverified, get("/api/competitions")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.competitions[0].verified").value(true));
    }

    @ParameterizedTest
    @EnumSource(Competition.PublicationStatus.class)
    void adminCreationIsAlwaysVerifiedForEveryPublicationStateAndKeepsExistingVisibility(Competition.PublicationStatus state) throws Exception {
        mvc.perform(as(admin, post(PATH)).contentType(MediaType.APPLICATION_JSON)
                        .content(creationBody(state.name(), "\"UNVERIFIED\"")))
                .andExpect(status().isCreated());
        Competition created = competitions.findAll().getFirst();
        mvc.perform(as(admin, get(PATH).param("publicationStatus", state.name())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.competitions[0].verificationStatus").value("MANUALLY_VERIFIED"));
        mvc.perform(as(admin, get(PATH)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements")
                        .value(state == Competition.PublicationStatus.ARCHIVED ? 0 : 1));
        mvc.perform(as(verified, get("/api/competitions")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements")
                        .value(state == Competition.PublicationStatus.DRAFT || state == Competition.PublicationStatus.PUBLISHED ? 1 : 0));
        mvc.perform(as(verified, get("/api/competitions/" + created.getId())))
                .andExpect(state == Competition.PublicationStatus.PUBLISHED ? status().isOk() : status().isNotFound());
    }

    @Test
    void explicitNullKeepsLegacyVerifiedDefault() throws Exception {
        mvc.perform(as(admin, post(PATH)).contentType(MediaType.APPLICATION_JSON).content(creationBody("DRAFT", "null")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationStatus").value("MANUALLY_VERIFIED"))
                .andExpect(jsonPath("$.verifiedAt").isNotEmpty());
        assertThat(competitions.findAll().getFirst().getVerifiedByUser().getId()).isEqualTo(admin.getId());
        assertThat(logs.findAll().getFirst().getToStatus()).isEqualTo(Competition.VerificationStatus.MANUALLY_VERIFIED);
    }

    @Test
    void existingFieldValidationStillRejectsWithoutPartialWrites() throws Exception {
        String valid = creationBody("DRAFT", "\"UNVERIFIED\"");
        for (String body : List.of(
                valid.replace("상태 선택", " "),
                valid.replace("\"WEB_MOBILE_IT\"", "null"),
                valid.replace("\"summary\":\"소개\"", "\"summary\":\"\""),
                valid.replace("https://example.com/verification-create", "not-a-url"),
                valid.replace("\"primary\":true", "\"primary\":false"),
                valid.replace("\"publicationStatus\":\"DRAFT\"", "\"publicationStatus\":null"),
                valid.replace("\"summary\":\"소개\"", "\"summary\":\"소개\",\"applicationStartAt\":\"2026-10-24T12:00:00Z\",\"applicationEndAt\":\"2026-10-23T12:00:00Z\""))) {
            mvc.perform(as(admin, post(PATH)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
            assertThat(competitions.count()).isZero();
            assertThat(urls.count()).isZero();
            assertThat(logs.count()).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"NOT_A_STATUS\"", "123", "true", "[]", "{\"status\":\"UNVERIFIED\"}"})
    void retiredInputIsIgnoredRegardlessOfItsValueOrJsonType(String value) throws Exception {
        mvc.perform(as(admin, post(PATH)).contentType(MediaType.APPLICATION_JSON).content(creationBody("DRAFT", value)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationStatus").value("MANUALLY_VERIFIED"));
        assertThat(competitions.findAll()).hasSize(1).allSatisfy(c -> {
            assertThat(c.getVerificationStatus()).isEqualTo(Competition.VerificationStatus.MANUALLY_VERIFIED);
            assertThat(c.getVerifiedByUser().getId()).isEqualTo(admin.getId());
        });
        assertThat(logs.findAll()).hasSize(1).allSatisfy(log ->
                assertThat(log.getToStatus()).isEqualTo(Competition.VerificationStatus.MANUALLY_VERIFIED));
    }

    @Test
    void retiredVerificationInputDoesNotRelaxCreationAuthorization() throws Exception {
        String body = creationBody("DRAFT", "\"UNVERIFIED\"");
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        for (User student : List.of(verified, unverified)) {
            mvc.perform(as(student, post(PATH)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }
        String oldToken = jwt.generateAccessToken(admin);
        ReflectionTestUtils.setField(admin, "role", User.Role.STUDENT);
        users.saveAndFlush(admin);
        mvc.perform(withToken(oldToken, post(PATH)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        assertThat(competitions.count()).isZero();
        assertThat(logs.count()).isZero();
    }

    @Test
    void legacyUnverifiedCreationRemainsUnverifiedAndListedAfterEditingAndPublishingThenArchiving() throws Exception {
        // Legacy data is not rewritten just because new administrator creations are always verified.
        Competition created = competitions.saveAndFlush(Competition.create("기존 미인증 등록", Competition.Category.WEB_MOBILE_IT,
                "주최", "대학생", "기존 소개", null, null, Competition.PublicationStatus.DRAFT,
                Competition.VerificationStatus.UNVERIFIED, null));
        logs.saveAndFlush(CompetitionVerificationLog.createAdminReviewLog(created, null,
                Competition.VerificationStatus.UNVERIFIED, admin, CompetitionVerificationLog.ADMIN_CREATION_REASON));
        service.updateByAdmin(otherAdmin.getId(), created.getId(), request("수정", Competition.Category.ETC, Competition.PublicationStatus.DRAFT));
        service.publishByAdmin(otherAdmin.getId(), created.getId());
        mvc.perform(as(admin, get(PATH))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.competitions[0].publicationStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.competitions[0].verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.competitions[0].verifiedAt").value(nullValue()));
        assertThat(competitions.findById(created.getId()).orElseThrow().getVerifiedByUser()).isNull();
        assertThat(logs.count()).isEqualTo(1);
        assertThat(logs.findAll().getFirst().getActorUser().getId()).isEqualTo(admin.getId());
        service.deleteByAdmin(otherAdmin.getId(), created.getId());
        mvc.perform(as(admin, get(PATH).param("publicationStatus", "ARCHIVED")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.competitions[0].verificationStatus").value("UNVERIFIED"));
    }

    @Test
    void samplesKeepTheirPreviousVerifiedDefaultAndCreationLogs() {
        var seeded = seeds.seedSamples(admin.getId());
        assertThat(seeded.createdCount()).isPositive();
        assertThat(competitions.findAll()).allSatisfy(competition -> {
            assertThat(competition.getVerificationStatus()).isEqualTo(Competition.VerificationStatus.MANUALLY_VERIFIED);
            assertThat(competition.getVerifiedAt()).isNotNull();
            assertThat(competition.getVerifiedByUser().getId()).isEqualTo(admin.getId());
        });
        assertThat(logs.findAll()).hasSize(seeded.createdCount()).allSatisfy(log -> {
            assertThat(log.getToStatus()).isEqualTo(Competition.VerificationStatus.MANUALLY_VERIFIED);
            assertThat(log.getActorUser().getId()).isEqualTo(admin.getId());
        });
    }

    @ParameterizedTest
    @EnumSource(Competition.PublicationStatus.class)
    void defaultExcludesArchivedAndExplicitFilterSelectsEachState(Competition.PublicationStatus state) throws Exception {
        AdminCompetitionResponse created = create(admin, "상태별", Competition.Category.WEB_MOBILE_IT, state);
        int expected = state == Competition.PublicationStatus.ARCHIVED ? 0 : 1;
        mvc.perform(as(admin, get(PATH))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(expected));
        mvc.perform(as(admin, get(PATH).param("publicationStatus", state.name())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.competitions[0].id").value(created.id()));
        mvc.perform(as(admin, get(PATH).param("publicationStatus",
                        state == Competition.PublicationStatus.DRAFT ? "PUBLISHED" : "DRAFT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.competitions").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "FROM", "METHOD", "ACTOR", "REASON"})
    void manualVerificationOrIncompleteCreationEvidenceDoesNotCountAsAdminCreation(String missing) throws Exception {
        Competition target = competitions.saveAndFlush(Competition.create("출처 미확인", Competition.Category.ETC,
                "주최", null, "소개", null, null, Competition.PublicationStatus.PUBLISHED,
                Competition.VerificationStatus.MANUALLY_VERIFIED, admin));
        if (!"MISSING".equals(missing)) {
            CompetitionVerificationLog log = CompetitionVerificationLog.createAdminReviewLog(target, null,
                    Competition.VerificationStatus.MANUALLY_VERIFIED, admin, CompetitionVerificationLog.ADMIN_CREATION_REASON);
            switch (missing) {
                case "FROM" -> ReflectionTestUtils.setField(log, "fromStatus", Competition.VerificationStatus.UNVERIFIED);
                case "METHOD" -> ReflectionTestUtils.setField(log, "verificationMethod", CompetitionVerificationLog.Method.AUTO_EXTRACTION);
                case "ACTOR" -> ReflectionTestUtils.setField(log, "actorUser", null);
                case "REASON" -> ReflectionTestUtils.setField(log, "reason", "관리자 검토 완료");
            }
            logs.saveAndFlush(log);
        }
        mvc.perform(as(admin, get(PATH))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void bothAdminsCreationAndSeedCreationRemainVisibleEvenIfCreatorRoleChanges() throws Exception {
        create(admin, "내 등록", Competition.Category.ETC, Competition.PublicationStatus.PUBLISHED);
        create(otherAdmin, "다른 관리자 등록", Competition.Category.ETC, Competition.PublicationStatus.DRAFT);
        var seeded = seeds.seedSamples(otherAdmin.getId());
        assertThat(seeded.createdCount()).isPositive();
        ReflectionTestUtils.setField(otherAdmin, "role", User.Role.STUDENT);
        users.saveAndFlush(otherAdmin);
        mvc.perform(as(admin, get(PATH).param("size", "1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(seeded.createdCount() + 2));
    }

    @Test
    void duplicateCreationLogsDoNotDuplicatePagesAndUrlsStayWithTheirCompetition() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            var created = create(i % 2 == 0 ? admin : otherAdmin, "중복 이력 " + i,
                    Competition.Category.WEB_MOBILE_IT, Competition.PublicationStatus.DRAFT);
            ids.add(created.id());
            Competition target = competitions.findById(created.id()).orElseThrow();
            logs.saveAndFlush(CompetitionVerificationLog.createAdminReviewLog(target, null,
                    Competition.VerificationStatus.MANUALLY_VERIFIED, admin, CompetitionVerificationLog.ADMIN_CREATION_REASON));
        }
        Collections.reverse(ids);
        var first = mvc.perform(as(admin, get(PATH)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.competitions.length()").value(10));
        for (int i = 0; i < 10; i++) {
            first.andExpect(jsonPath("$.competitions[" + i + "].id").value(ids.get(i)))
                    .andExpect(jsonPath("$.competitions[" + i + "].urls.length()").value(1))
                    .andExpect(jsonPath("$.competitions[" + i + "].urls[0].url").value("https://example.com/created/" + (12 - i)));
        }
        mvc.perform(as(admin, get(PATH).param("page", "1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.hasNext").value(false)).andExpect(jsonPath("$.competitions.length()").value(2))
                .andExpect(jsonPath("$.competitions[0].id").value(ids.get(10)));
        mvc.perform(as(admin, get(PATH).param("page", "2")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.competitions").isEmpty());
    }

    @Test
    void keywordCategoryAndPublicationStatusCombineAsAndConditions() throws Exception {
        var match = create(admin, " 데이터 공모전 ", Competition.Category.WEB_MOBILE_IT, Competition.PublicationStatus.DRAFT);
        create(admin, "다른 이름", Competition.Category.WEB_MOBILE_IT, Competition.PublicationStatus.DRAFT);
        create(admin, "데이터", Competition.Category.ETC, Competition.PublicationStatus.DRAFT);
        create(admin, "데이터", Competition.Category.WEB_MOBILE_IT, Competition.PublicationStatus.PUBLISHED);
        mvc.perform(as(admin, get(PATH).param("keyword", " 데이터 ").param("category", "WEB_MOBILE_IT")
                        .param("publicationStatus", "DRAFT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.competitions[0].id").value(match.id()));
        mvc.perform(as(admin, get(PATH).param("keyword", " 검색주최 ")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(as(admin, get(PATH).param("keyword", "   ")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void currentStateAndUrlsAreReturnedAfterUpdatesPublishingAndArchiving() throws Exception {
        var created = create(admin, "처음", Competition.Category.ETC, Competition.PublicationStatus.DRAFT);
        service.updateByAdmin(otherAdmin.getId(), created.id(), request("수정", Competition.Category.ETC, Competition.PublicationStatus.DRAFT));
        service.publishByAdmin(otherAdmin.getId(), created.id());
        mvc.perform(as(admin, get(PATH))).andExpect(status().isOk())
                .andExpect(jsonPath("$.competitions[0].title").value("수정"))
                .andExpect(jsonPath("$.competitions[0].publicationStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.competitions[0].urls[0].url").value("https://example.com/created/2"));
        service.deleteByAdmin(otherAdmin.getId(), created.id());
        mvc.perform(as(admin, get(PATH))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(as(admin, get(PATH).param("publicationStatus", "ARCHIVED")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(as(verified, get("/api/competitions"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anonymousAndOrdinaryUsersCannotReadAdminFields() throws Exception {
        create(admin, "권한", Competition.Category.ETC, Competition.PublicationStatus.DRAFT);
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        for (User student : List.of(verified, unverified)) {
            mvc.perform(as(student, get(PATH))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.competitions").doesNotExist());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "SUSPENDED", "DELETED"})
    void staleAdminJwtDoesNotBypassCurrentDatabaseAuthorization(String changed) throws Exception {
        String oldToken = jwt.generateAccessToken(admin);
        if ("STUDENT".equals(changed)) ReflectionTestUtils.setField(admin, "role", User.Role.STUDENT);
        else ReflectionTestUtils.setField(admin, "status", User.Status.valueOf(changed));
        users.saveAndFlush(admin);
        mvc.perform(withToken(oldToken, get(PATH))).andExpect(status().isForbidden());
    }

    @Test
    void emptyListAndInvalidParametersPreserveExistingValidationConventions() throws Exception {
        mvc.perform(as(admin, get(PATH))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.competitions").isEmpty());
        for (String[] param : List.of(new String[]{"page", "-1"}, new String[]{"page", "abc"},
                new String[]{"size", "0"}, new String[]{"size", "21"}, new String[]{"keyword", "x".repeat(101)},
                new String[]{"category", "INVALID"}, new String[]{"publicationStatus", "INVALID"})) {
            mvc.perform(as(admin, get(PATH).param(param[0], param[1]))).andExpect(status().isBadRequest());
        }
    }

    private String creationBody(String publicationStatus, String verificationJson) {
        return "{\"title\":\"상태 선택\",\"category\":\"WEB_MOBILE_IT\",\"summary\":\"소개\","
                + "\"publicationStatus\":\"" + publicationStatus + "\",\"verificationStatus\":" + verificationJson + ","
                + "\"urls\":[{\"type\":\"RECRUITMENT\",\"url\":\"https://example.com/verification-create\",\"primary\":true}]}";
    }

    private AdminCompetitionResponse create(User creator, String title, Competition.Category category, Competition.PublicationStatus state) {
        return service.createByAdmin(creator.getId(), CreateCompetitionRequest.from(request(title, category, state)));
    }

    private UpsertCompetitionRequest request(String title, Competition.Category category, Competition.PublicationStatus state) {
        return new UpsertCompetitionRequest(title, category, "검색주최", "대학생", "소개", null, null, state,
                List.of(new CompetitionUrlRequest(CompetitionUrl.Type.RECRUITMENT,
                        "https://example.com/created/" + (++urlSequence), true)));
    }

    private MockHttpServletRequestBuilder as(User actor, MockHttpServletRequestBuilder request) {
        return withToken(jwt.generateAccessToken(actor), request);
    }

    private MockHttpServletRequestBuilder withToken(String token, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token).with(servletRequest -> {
            servletRequest.setServletPath(servletRequest.getRequestURI());
            return servletRequest;
        });
    }
}
