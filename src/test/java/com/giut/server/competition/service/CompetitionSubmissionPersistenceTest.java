package com.giut.server.competition.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.competition.dto.request.CompetitionSubmissionForm;
import com.giut.server.competition.dto.request.CompetitionSubmissionRequest;
import com.giut.server.competition.dto.response.CompetitionSubmissionResponse;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.competition.repository.CompetitionUrlRepository;
import com.giut.server.competition.repository.CompetitionVerificationLogRepository;
import com.giut.server.competition.util.PublicCompetitionSource;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_competition_submissions;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
class CompetitionSubmissionPersistenceTest {
    private static final String COMPETITIONS = "/api/competitions";
    private static final String PREVIEW = COMPETITIONS + "/extract-preview";
    private static final String SUBMISSIONS = COMPETITIONS + "/submissions";
    private static final String ADMIN = "/api/admin/competitions";
    private static final String SOURCE = "https://example.com/contest";
    private static final String PREVIEW_BODY = """
            {"url":"https://example.com/contest"}
            """;
    private static final String FORM = """
            {"title":"학생 공모전","category":"WEB_MOBILE_IT","summary":"공고 설명",
             "activityType":"COMPETITION","applicationEndAt":"2099-01-01T00:00:00Z"}
            """;
    private static final String APPROVE = """
            {"decision":"APPROVE"}
            """;

    @Autowired private WebApplicationContext context;
    @Autowired private JwtProvider jwt;
    @Autowired private UserRepository users;
    @Autowired private CompetitionRepository competitions;
    @Autowired private CompetitionUrlRepository urls;
    @Autowired private CompetitionVerificationLogRepository logs;
    @Autowired private CompetitionSubmissionService submissions;
    @MockitoBean private PublicCompetitionSource publicSource;
    @MockitoBean private AiCompetitionExtractClient extractor;

    private MockMvc mvc;
    private User admin;
    private User member;
    private User unverified;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin = users.saveAndFlush(User.createAdmin("submit-admin@example.com", "hash", "관리자"));
        member = User.createOAuthUser("submit-member@example.com", "학생", User.OAuthProvider.KAKAO, "submit-member");
        member.verifyUniversityEmail("submit-member@uos.ac.kr");
        member = users.saveAndFlush(member);
        unverified = users.saveAndFlush(User.createOAuthUser("submit-unverified@example.com", "미인증",
                User.OAuthProvider.KAKAO, "submit-unverified"));
    }

    @AfterEach
    void cleanUp() {
        logs.deleteAll();
        urls.deleteAll();
        competitions.deleteAll();
        users.deleteAll();
    }

    @Test
    void existingRecruitmentUrlReturnsOnlyLinkBeforeCheckingOrCallingAi() throws Exception {
        String existingForm = """
                {"title":"기존 공고","category":"WEB_MOBILE_IT","summary":"소개","publicationStatus":"PUBLISHED",
                 "applicationEndAt":"2099-01-01T00:00:00Z",
                 "urls":[{"type":"RECRUITMENT","url":"https://example.com/contest","primary":true}]}
                """;
        mvc.perform(json(post(ADMIN), admin, existingForm)).andExpect(status().isCreated());

        mvc.perform(json(post(PREVIEW), member, """
                {"url":"https://EXAMPLE.COM:443/contest/"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXISTS"))
                .andExpect(jsonPath("$.url").value(SOURCE))
                .andExpect(jsonPath("$.draft").doesNotExist())
                .andExpect(jsonPath("$.requiredInputs").doesNotExist());
        verifyNoInteractions(publicSource, extractor);
    }

    @Test
    void previewReturnsMissingFieldsAndNormalizesAiCodesAndDates() throws Exception {
        when(extractor.extract(SOURCE)).thenReturn(extraction(null));
        mvc.perform(json(post(PREVIEW), member, PREVIEW_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.draft.title").value("학생 공모전"))
                .andExpect(jsonPath("$.draft.category").value("WEB_MOBILE_IT"))
                .andExpect(jsonPath("$.draft.activityType").value("COMPETITION"))
                .andExpect(jsonPath("$.draft.applicationStartAt").value("2098-12-31T15:00:00Z"))
                .andExpect(jsonPath("$.draft.urls[0].primary").value(true))
                .andExpect(jsonPath("$.requiredInputs[0].field").value("applicationEndAt"))
                .andExpect(jsonPath("$.requiredInputs[0].type").value("datetime"));
        assertThat(competitions.count()).isZero();

        when(extractor.extract(SOURCE)).thenReturn(extraction(LocalDate.of(2099, 1, 2)));
        mvc.perform(json(post(PREVIEW), member, PREVIEW_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.draft.applicationEndAt").value("2099-01-02T14:59:59Z"));
        assertThat(competitions.count()).isZero();
    }

    @Test
    void memberFillsFieldsThenAdminApprovalIsRequiredToPublish() throws Exception {
        mvc.perform(json(post(SUBMISSIONS), member, submission("""
                {"title":"학생 공모전"}
                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requiredInputs[*].field", hasItem("applicationEndAt")));
        assertThat(competitions.count()).isZero();

        String forgedForm = FORM.strip().replaceFirst("\\{",
                "{\"publicationStatus\":\"PUBLISHED\",\"verificationStatus\":\"MANUALLY_VERIFIED\",");
        mvc.perform(json(post(SUBMISSIONS), member, submission(forgedForm)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicationStatus").value("DRAFT"))
                .andExpect(jsonPath("$.verificationStatus").value("MANUAL_REVIEW_PENDING"));
        Long id = competitions.findAll().getFirst().getId();

        mvc.perform(json(post(SUBMISSIONS), member, """
                {"sourceUrl":"https://EXAMPLE.COM/contest/"}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.url").value(SOURCE));
        mvc.perform(as(get(COMPETITIONS), member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(as(get(COMPETITIONS + "/" + id), member)).andExpect(status().isNotFound());

        mvc.perform(as(get(ADMIN).param("verificationStatus", "MANUAL_REVIEW_PENDING"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competitions[0].registrationSource").value("USER"))
                .andExpect(jsonPath("$.competitions[0].submittedByUserId").value(member.getId()))
                .andExpect(jsonPath("$.competitions[0].submissionSourceUrl").value(SOURCE));
        mvc.perform(json(patch(ADMIN + "/" + id + "/review"), member, APPROVE)).andExpect(status().isForbidden());
        mvc.perform(as(patch(ADMIN + "/" + id + "/publish"), admin)).andExpect(status().isConflict());
        mvc.perform(json(put(ADMIN + "/" + id), admin, """
                {"title":"우회 수정","category":"WEB_MOBILE_IT","summary":"소개","publicationStatus":"PUBLISHED",
                 "applicationEndAt":"2099-01-01T00:00:00Z",
                 "urls":[{"type":"SOURCE_ORIGINAL","url":"https://example.com/contest","primary":true}]}
                """)).andExpect(status().isConflict());

        mvc.perform(json(patch(ADMIN + "/" + id + "/review"), admin, APPROVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicationStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.verificationStatus").value("MANUALLY_VERIFIED"))
                .andExpect(jsonPath("$.verifiedAt").isNotEmpty());
        mvc.perform(as(get(COMPETITIONS + "/" + id), member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
        mvc.perform(json(patch(ADMIN + "/" + id + "/review"), admin, APPROVE)).andExpect(status().isConflict());
        assertThat(logs.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getActorUser().getId()).isEqualTo(admin.getId());
            assertThat(log.getFromStatus()).isEqualTo(Competition.VerificationStatus.MANUAL_REVIEW_PENDING);
            assertThat(log.getToStatus()).isEqualTo(Competition.VerificationStatus.MANUALLY_VERIFIED);
        });
        verifyNoInteractions(extractor);
    }

    @Test
    void rejectionRequiresReasonAndDoesNotExposeSubmission() throws Exception {
        mvc.perform(json(post(SUBMISSIONS), member, submission(FORM))).andExpect(status().isCreated());
        Long id = competitions.findAll().getFirst().getId();
        mvc.perform(json(patch(ADMIN + "/" + id + "/review"), admin, """
                {"decision":"REJECT"}
                """)).andExpect(status().isBadRequest());
        mvc.perform(json(patch(ADMIN + "/" + id + "/review"), admin, """
                {"decision":"REJECT","reason":"공식 공고를 확인할 수 없음"}
                """)).andExpect(status().isOk())
                .andExpect(jsonPath("$.publicationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.verificationStatus").value("REJECTED"));
        mvc.perform(as(get(COMPETITIONS + "/" + id), member)).andExpect(status().isNotFound());
        assertThat(logs.findAll()).singleElement()
                .satisfies(log -> assertThat(log.getReason()).isEqualTo("공식 공고를 확인할 수 없음"));
    }

    @Test
    void protectedEndpointsAndUpstreamErrorKeepTheirStatus() throws Exception {
        mvc.perform(post(PREVIEW).contentType(MediaType.APPLICATION_JSON).content(PREVIEW_BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post(PREVIEW), unverified, PREVIEW_BODY)).andExpect(status().isForbidden());
        mvc.perform(json(post(SUBMISSIONS), unverified, submission(FORM))).andExpect(status().isForbidden());
        verifyNoInteractions(extractor);

        when(extractor.extract(SOURCE)).thenThrow(new AiCompetitionExtractClient.ExtractionUnavailable(
                HttpStatus.GATEWAY_TIMEOUT, "추출 시간 초과"));
        mvc.perform(json(post(PREVIEW), member, PREVIEW_BODY))
                .andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.code").value(504));
    }

    @Test
    void concurrentSubmissionsOfSameSourceCreateOneDraft() throws Exception {
        CompetitionSubmissionForm form = new CompetitionSubmissionForm("학생 공모전", Competition.Category.WEB_MOBILE_IT,
                null, null, "공고 설명", null, Instant.parse("2099-01-01T00:00:00Z"),
                List.of(), Competition.ActivityType.COMPETITION, null, null, null, null);
        var barrier = new CountDownLatch(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                barrier.countDown();
                assertThat(barrier.await(5, TimeUnit.SECONDS)).isTrue();
                return submissions.submit(member.getId(), new CompetitionSubmissionRequest(SOURCE, form));
            });
            var second = executor.submit(() -> {
                barrier.countDown();
                assertThat(barrier.await(5, TimeUnit.SECONDS)).isTrue();
                return submissions.submit(admin.getId(), new CompetitionSubmissionRequest(
                        "https://EXAMPLE.COM/contest/", form));
            });
            assertThat(List.of(first.get(10, TimeUnit.SECONDS).status(), second.get(10, TimeUnit.SECONDS).status()))
                    .containsExactlyInAnyOrder(CompetitionSubmissionResponse.Status.SUBMITTED,
                            CompetitionSubmissionResponse.Status.EXISTS);
        }
        assertThat(competitions.count()).isEqualTo(1);
        assertThat(urls.count()).isEqualTo(1);
        verifyNoInteractions(extractor);
    }

    private AiCompetitionExtractClient.Extraction extraction(LocalDate deadline) {
        return new AiCompetitionExtractClient.Extraction("학생 공모전", "기관", "대학생", null,
                LocalDate.of(2099, 1, 1), deadline, null, null, "CONTEST",
                List.of("IT_DEVELOP"), false, "공고 설명", null, null);
    }

    private String submission(String form) {
        return "{\"sourceUrl\":\"" + SOURCE + "\",\"draft\":" + form + "}";
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, User actor, String body) {
        return as(request, actor).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User actor) {
        return request.header("Authorization", "Bearer " + jwt.generateAccessToken(actor)).with(servletRequest -> {
            servletRequest.setServletPath(servletRequest.getRequestURI());
            return servletRequest;
        });
    }
}
