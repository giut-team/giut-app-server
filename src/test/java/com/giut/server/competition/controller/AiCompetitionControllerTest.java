package com.giut.server.competition.controller;

import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.competition.repository.CompetitionUrlRepository;
import com.giut.server.competition.repository.CompetitionVerificationLogRepository;
import com.giut.server.competition.dto.request.AiTargetParticipants;
import com.giut.server.auth.security.JwtProvider;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_ai_ingestion;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.ai-ingestion.secret=0123456789abcdef0123456789abcdef",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL="
})
class AiCompetitionControllerTest {
    private static final String PATH = "/api/integrations/competitions";
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final String BODY = """
            {"title":"AI 공모전", "prizeInfo":"상금 미정", "activityType":"HACKATHON", "category":"WEB_MOBILE_IT", "targetParticipants":"UNIV_ONLY",
             "applicationStartAt":"2026-11-01T00:00:00Z", "applicationEndAt":"2026-12-01T00:00:00Z",
             "urls":[{"type":"OFFICIAL","url":"https://example.com/ai-contest","primary":true}]}
            """;
    @Autowired WebApplicationContext context;
    @Autowired CompetitionRepository competitions;
    @Autowired CompetitionUrlRepository urls;
    @Autowired CompetitionVerificationLogRepository logs;
    @Autowired UserRepository users;
    @Autowired JwtProvider jwt;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        logs.deleteAll();
        urls.deleteAll();
        competitions.deleteAll();
        users.deleteAll();
    }

    @Test
    void aiTargetAndPrizeAreSavedAndPublished() throws Exception {
        String body = BODY.replace("UNIV_ONLY", "NONE")
                .replace("상금 미정", "총상금 1,000만 원");
        mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.activityType").value("HACKATHON"))
                .andExpect(jsonPath("$.targetParticipants").value("NONE"))
                .andExpect(jsonPath("$.prizeInfo").value("총상금 1,000만 원"))
                .andExpect(jsonPath("$.publicationStatus").value("PUBLISHED"));
        Long id = competitions.findAll().getFirst().getId();
        User user = users.saveAndFlush(User.createOAuthUser("prize@example.com", "학생", User.OAuthProvider.KAKAO, "prize"));
        mvc.perform(get("/api/competitions/" + id).header("Authorization", "Bearer " + jwt.generateAccessToken(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.prizeInfo").value("총상금 1,000만 원"))
                .andExpect(jsonPath("$.targetParticipants").value("NONE"));
    }

    @Test
    void totalPrizeAmountIsOptionalAndSavedForSingleAndBatch() throws Exception {
        String single = BODY.replace("\"title\":\"AI 공모전\",", "\"total_prize_amount\":10000000,\"title\":\"AI 공모전\",");
        mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(single))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.totalPrizeAmount").value(10000000));
        Long id = competitions.findAll().getFirst().getId();
        User user = users.saveAndFlush(User.createOAuthUser("amount@example.com", "학생", User.OAuthProvider.KAKAO, "amount"));
        mvc.perform(get("/api/competitions/" + id).header("Authorization", "Bearer " + jwt.generateAccessToken(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPrizeAmount").value(10000000));
        String withAmount = BODY.replace("ai-contest", "with-amount")
                .replace("\"title\":\"AI 공모전\",", "\"total_prize_amount\":0,\"title\":\"AI 공모전\",");
        String withoutAmount = BODY.replace("ai-contest", "no-amount");
        mvc.perform(post(PATH + "/batch").header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"competitions\":[" + withAmount + "," + withoutAmount + "]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].totalPrizeAmount").value(0))
                .andExpect(jsonPath("$[1].totalPrizeAmount").value(org.hamcrest.Matchers.nullValue()));
        assertThat(competitions.findById(id).orElseThrow().getTotalPrizeAmount()).isEqualTo(10000000L);
    }

    @ParameterizedTest
    @EnumSource(AiTargetParticipants.class)
    void allAiParticipantCodesAreSaved(AiTargetParticipants participants) throws Exception {
        mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("UNIV_ONLY", participants.name())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.targetParticipants").value(participants.name()));
        assertThat(competitions.findAll().getFirst().getTargetParticipants()).isEqualTo(participants.name());
    }

    @ParameterizedTest
    @CsvSource({
            "IDEA, PLANNING_IDEA", "MARKETING, ADVERTISING_MARKETING", "AI_DATA, AI_DATA",
            "IT_DEVELOP, WEB_MOBILE_IT", "SCIENCE_ENGINEERING, SCIENCE_ENGINEERING",
            "DESIGN, DESIGN_CHARACTER_WEBTOON", "PHOTO_CONTENTS, VIDEO_UCC_PHOTO",
            "LITERATURE, LITERATURE_WRITING_SCENARIO", "RESEARCH, PAPER_REPORT",
            "CULTURE_ARTS, ENTERTAINMENT_ART_MUSIC", "ETC, ETC", "WEB_MOBILE_IT, WEB_MOBILE_IT"
    })
    void aiCategoriesAreMappedAndStored(String aiCategory, String expectedCategory) throws Exception {
        String body = BODY.replace("WEB_MOBILE_IT", aiCategory);
        mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.category").value(expectedCategory));
        assertThat(competitions.findAll().getFirst().getCategory().name()).isEqualTo(expectedCategory);
    }

    @Test
    void batchMapsAiCategoriesAndRejectsUnknownCode() throws Exception {
        String first = BODY.replace("WEB_MOBILE_IT", "IDEA");
        String second = BODY.replace("WEB_MOBILE_IT", "IT_DEVELOP").replace("ai-contest", "second");
        mvc.perform(post(PATH + "/batch").header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"competitions\":[" + first + "," + second + "]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].category").value("PLANNING_IDEA"))
                .andExpect(jsonPath("$[1].category").value("WEB_MOBILE_IT"));
        mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("WEB_MOBILE_IT", "UNKNOWN")))
                .andExpect(status().isBadRequest());
        assertThat(competitions.count()).isEqualTo(2);
    }

    @Test
    void invalidAndRetiredActivityTypesAreNotRegistered() throws Exception {
        for (String value : new String[] {"INVALID_ACTIVITY", "NOT_ACTIVITY", "MULTIPLE_ACTIVITIES", "INTERNSHIP"}) {
            mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                            .content(BODY.replace("HACKATHON", value)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(competitions.count()).isZero();
    }

    @Test
    void invalidActivityInBatchRollsBackEverything() throws Exception {
        String batch = "{\"competitions\":[" + BODY + "," + BODY.replace("HACKATHON", "INVALID_ACTIVITY").replace("ai-contest", "invalid-contest") + "]}";
        mvc.perform(post(PATH + "/batch").header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isBadRequest());
        assertThat(competitions.count()).isZero();
        assertThat(urls.count()).isZero();
        assertThat(logs.count()).isZero();
    }

    @Test
    void missingAndBlankPrizeInfoDefaultsForSingleAndBatch() throws Exception {
        String[] variants = {
                BODY.replace("\"prizeInfo\":\"상금 미정\", ", ""),
                BODY.replace("\"상금 미정\"", "null"),
                BODY.replace("상금 미정", ""),
                BODY.replace("상금 미정", "   ")
        };
        for (int i = 0; i < variants.length; i++) {
            String single = variants[i].replace("ai-contest", "single-" + i);
            String batchItem = variants[i].replace("ai-contest", "batch-" + i);
            mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(single))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.prizeInfo").value("상금 미정"));
            mvc.perform(post(PATH + "/batch").header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"competitions\":[" + batchItem + "]}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$[0].prizeInfo").value("상금 미정"));
        }
        assertThat(competitions.count()).isEqualTo(8);
        assertThat(competitions.findAll()).allSatisfy(competition ->
                assertThat(competition.getPrizeInfo()).isEqualTo("상금 미정"));
    }

    @Test
    void prizeInfoOverLimitIsRejected() throws Exception {
        mvc.perform(post(PATH).header("X-AI-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("상금 미정", "가".repeat(3001))))
                .andExpect(status().isBadRequest());
        assertThat(competitions.count()).isZero();
    }

    @Test
    void batchRequiresKeyAndSavesAllInOrder() throws Exception {
        String batch = "{\"competitions\":[" + BODY + "," + BODY.replace("ai-contest", "second").replace("HACKATHON", "CONTEST") + "]}";
        mvc.perform(post(PATH + "/batch").contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH + "/batch").header("X-AI-API-Key", "wrong").contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH + "/batch").header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].activityType").value("HACKATHON"))
                .andExpect(jsonPath("$[1].activityType").value("COMPETITION"))
                .andExpect(jsonPath("$[0].verificationStatus").value("AUTO_VERIFIED"));
        assertThat(competitions.count()).isEqualTo(2);
        assertThat(urls.count()).isEqualTo(2);
        assertThat(logs.count()).isEqualTo(2);
    }

    @Test
    void sharedSourcesAndMissingOfficialAreAllowedButDuplicateOfficialRollsBack() throws Exception {
        String commonSource = "{\"type\":\"SOURCE_ORIGINAL\",\"url\":\"https://example.com/shared\",\"primary\":true}";
        String first = BODY.replace("{\"type\":\"OFFICIAL\",\"url\":\"https://example.com/ai-contest\",\"primary\":true}", commonSource);
        String second = first.replace("AI 공모전", "다른 공모전");
        mvc.perform(post(PATH + "/batch").header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"competitions\":[" + first + "," + second + "]}"))
                .andExpect(status().isCreated());
        assertThat(competitions.count()).isEqualTo(2);
        assertThat(urls.count()).isEqualTo(2);
    }

    @Test
    void differentOfficialsCanShareSourceAndRecruitmentAcrossCompetitions() throws Exception {
        String shared = ", {\"type\":\"SOURCE_ORIGINAL\",\"url\":\"https://example.com/shared\",\"primary\":false},"
                + " {\"type\":\"RECRUITMENT\",\"url\":\"https://example.com/apply\",\"primary\":false}";
        String first = BODY.replace("\"primary\":true}]", "\"primary\":true}" + shared + "]");
        String second = first.replace("ai-contest", "other-official");
        mvc.perform(post(PATH + "/batch").header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"competitions\":[" + first + "," + second + "]}"))
                .andExpect(status().isCreated());
        assertThat(competitions.count()).isEqualTo(2);
        assertThat(urls.count()).isEqualTo(6);
    }

    @Test
    void repeatedUrlWithinOneCompetitionIsRejected() throws Exception {
        String sameUrl = BODY.replace("\"primary\":true}]", "\"primary\":true},"
                + "{\"type\":\"SOURCE_ORIGINAL\",\"url\":\"https://example.com/ai-contest\",\"primary\":false}]");
        mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(sameUrl))
                .andExpect(status().isBadRequest());
        assertThat(competitions.count()).isZero();
    }

    @Test
    void batchRollsBackOnDuplicateOrLaterInvalidDates() throws Exception {
        for (String second : new String[] {BODY, BODY.replace("ai-contest", "second")
                .replace("2026-12-01T00:00:00Z", "2026-10-01T00:00:00Z")}) {
            String batch = "{\"competitions\":[" + BODY + "," + second + "]}";
            mvc.perform(post(PATH + "/batch").header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(batch))
                    .andExpect(second.equals(BODY) ? status().isConflict() : status().isBadRequest());
            assertThat(competitions.count()).isZero();
            assertThat(urls.count()).isZero();
            assertThat(logs.count()).isZero();
        }
    }

    @Test
    void batchConflictWithExistingCompetitionLeavesExistingDataUntouched() throws Exception {
        mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        String batch = "{\"competitions\":[" + BODY.replace("ai-contest", "new-contest") + "," + BODY + "]}";
        mvc.perform(post(PATH + "/batch").header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isConflict());
        assertThat(competitions.count()).isEqualTo(1);
        assertThat(urls.count()).isEqualTo(1);
        assertThat(logs.count()).isEqualTo(1);
    }

    @Test
    void batchAcceptsTwentyCompetitions() throws Exception {
        String items = IntStream.range(0, 20).mapToObj(i -> BODY.replace("ai-contest", "contest-" + i))
                .collect(Collectors.joining(","));
        mvc.perform(post(PATH + "/batch").header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"competitions\":[" + items + "]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(20));
        assertThat(competitions.count()).isEqualTo(20);
        assertThat(urls.count()).isEqualTo(20);
        assertThat(logs.count()).isEqualTo(20);
    }

    @Test
    void batchRejectsEmptyNullAndOverLimit() throws Exception {
        String many = IntStream.range(0, 21).mapToObj(i -> BODY.replace("ai-contest", "contest-" + i))
                .collect(Collectors.joining(","));
        for (String batch : new String[] {"{\"competitions\":[]}", "{\"competitions\":[null]}",
                "{\"competitions\":[" + many + "]}"}) {
            mvc.perform(post(PATH + "/batch").header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(batch))
                    .andExpect(status().isBadRequest());
        }
        assertThat(competitions.count()).isZero();
    }

    @Test
    void aiDataCategoryPersistsAndCanBeFilteredInPublicList() throws Exception {
        String aiBody = BODY.replace("WEB_MOBILE_IT", "AI_DATA");
        mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(aiBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.category").value("AI_DATA"))
                .andExpect(jsonPath("$.activityType").value("HACKATHON"))
                .andExpect(jsonPath("$.activityTypeName").value("해커톤"));
        mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("ai-contest", "it-contest")))
                .andExpect(status().isCreated());
        mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(aiBody.replace("HACKATHON", "CONTEST").replace("ai-contest", "ai-competition")))
                .andExpect(status().isCreated());
        User user = users.saveAndFlush(User.createOAuthUser("ai-category@example.com", "학생", User.OAuthProvider.KAKAO, "ai-category"));
        mvc.perform(get("/api/competitions").param("activityType", "HACKATHON").param("category", "AI_DATA")
                        .header("Authorization", "Bearer " + jwt.generateAccessToken(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.competitions[0].category").value("AI_DATA"))
                .andExpect(jsonPath("$.competitions[0].activityTypeName").value("해커톤"))
                .andExpect(jsonPath("$.competitions[0].categoryName").value("AI/데이터"));
        mvc.perform(get("/api/competitions").param("activityType", "COMPETITION").param("category", "AI_DATA")
                        .header("Authorization", "Bearer " + jwt.generateAccessToken(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.competitions[0].activityType").value("COMPETITION"));
    }

    @Test
    void keyIsRequiredAndCannotUnlockOtherEndpoints() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("X-AI-API-Key", "wrong").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/ai/competitions").header("X-AI-API-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/competitions").header("X-AI-API-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        assertThat(competitions.count()).isZero();
    }

    @Test
    void verifiedAiSubmissionIsPublishedAndDuplicateCannotOverwrite() throws Exception {
        mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.publicationStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.verificationStatus").value("AUTO_VERIFIED"));
        mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
        assertThat(competitions.count()).isEqualTo(1);
        assertThat(urls.count()).isEqualTo(1);
        assertThat(logs.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getVerificationMethod().name()).isEqualTo("AUTO_EXTRACTION");
            assertThat(log.getActorUser()).isNull();
        });
    }

    @Test
    void requiredFieldsDatesAndPrimaryUrlAreValidated() throws Exception {
        for (String body : new String[] {
                BODY.replace("\"UNIV_ONLY\"", "\"INVALID\""),
                BODY.replace("2026-12-01T00:00:00Z", "2026-10-01T00:00:00Z"),
                BODY.replace("\"primary\":true", "\"primary\":false"),
                BODY.replace("\"activityType\":\"HACKATHON\",", ""),
                BODY.replace("HACKATHON", "INVALID"),
                BODY.replace("\"applicationStartAt\":\"2026-11-01T00:00:00Z\",", "")
        }) {
            mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(competitions.count()).isZero();
    }

    @Test
    void singleRequestAcceptsActualAiShapeWithCategoryArrayDateOnlyAndNoPrimary() throws Exception {
        String body = """
                {"title":"AI 공모전 테스트 1", "hostOrganization":"OO기업", "targetParticipants":"ALL",
                 "isForEnrolledOnly":false, "applicationStartAt":"2026-11-10", "applicationEndAt":"2026-12-10",
                 "totalPrizeAmount":5000000, "prizeInfo":"1등상: 200만원 / 2등상: 100만원 / 3등상: 50만원",
                 "activityKind":"CONTEST", "category":["AI_DATA","IDEA"], "isTeam":true,
                 "summary":"AI를 활용한 아이디어 공모전",
                 "urls":[{"type":"OFFICIAL","url":"https://example.com/real-shape-1"}]}
                """;
        mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("AI_DATA"))
                .andExpect(jsonPath("$.activityType").value("COMPETITION"))
                .andExpect(jsonPath("$.isForEnrolledOnly").value(false))
                .andExpect(jsonPath("$.isTeam").value(true));
        var saved = competitions.findAll().getFirst();
        assertThat(saved.getCategory()).isEqualTo(com.giut.server.competition.entity.Competition.Category.AI_DATA);
        assertThat(saved.getIsForEnrolledOnly()).isFalse();
        assertThat(saved.getIsTeam()).isTrue();
        assertThat(urls.findAllByCompetition_Id(saved.getId())).singleElement()
                .satisfies(url -> assertThat(url.isPrimary()).isTrue());
    }

    @Test
    void nullOfficialAndRecruitmentUrlsAreDroppedAndSourceOriginalBecomesPrimary() throws Exception {
        String body = """
                {"title":"AI 공모전 테스트 1", "hostOrganization":"OO기업", "targetParticipants":"ALL",
                 "applicationStartAt":"2026-11-10", "applicationEndAt":"2026-12-10",
                 "activityKind":"HACKATHON", "category":["AI_DATA"],
                 "summary":"AI를 활용한 아이디어 공모전",
                 "urls":[{"type":"OFFICIAL","url":null},{"type":"RECRUITMENT","url":null},
                         {"type":"SOURCE_ORIGINAL","url":"https://example.com/source-only"}]}
                """;
        mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        var saved = competitions.findAll().getFirst();
        assertThat(urls.findAllByCompetition_Id(saved.getId())).singleElement().satisfies(url -> {
            assertThat(url.getUrl()).isEqualTo("https://example.com/source-only");
            assertThat(url.isPrimary()).isTrue();
        });
    }

    @Test
    void batchAcceptsBareArrayMatchingActualAiPayload() throws Exception {
        String batch = """
                [
                  {"title":"AI 공모전 테스트 1", "hostOrganization":"OO기업", "targetParticipants":"ALL",
                   "isForEnrolledOnly":false, "applicationStartAt":"2026-11-10", "applicationEndAt":"2026-12-10",
                   "totalPrizeAmount":5000000, "prizeInfo":"1등상: 200만원 / 2등상: 100만원 / 3등상: 50만원",
                   "activityKind":"CONTEST", "category":["AI_DATA","IDEA"], "isTeam":true,
                   "summary":"AI를 활용한 아이디어 공모전",
                   "urls":[{"type":"OFFICIAL","url":"https://example.com/batch-shape-1"}]},
                  {"title":"AI 공모전 테스트 2", "hostOrganization":"OO재단", "targetParticipants":"UNIV_ONLY",
                   "isForEnrolledOnly":true, "applicationStartAt":"2026-11-10", "applicationEndAt":"2026-11-20",
                   "totalPrizeAmount":5000000, "prizeInfo":"1등상: 200만원 / 2등상: 100만원 / 3등상: 50만원",
                   "activityKind":"HACKATHON", "category":["IT_DEVELOP"], "isTeam":true,
                   "summary":"대학생 대상 해커톤",
                   "urls":[{"type":"OFFICIAL","url":"https://example.com/batch-shape-2"}]}
                ]
                """;
        mvc.perform(post(PATH + "/batch").header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].category").value("AI_DATA"))
                .andExpect(jsonPath("$[0].activityType").value("COMPETITION"))
                .andExpect(jsonPath("$[1].category").value("WEB_MOBILE_IT"))
                .andExpect(jsonPath("$[1].activityType").value("HACKATHON"));
        assertThat(competitions.count()).isEqualTo(2);
        assertThat(urls.count()).isEqualTo(2);
    }
}
