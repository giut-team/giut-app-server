package com.giut.server.user.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP service commits against a class-specific in-memory H2 DB; SMTP is mocked. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_university_revocation;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
class AdminUniversityVerificationPersistenceTest {
    private static final String STATUS = "/api/members/university-email/status";
    private static final String SEND = "/api/members/university-email/send";
    private static final String VERIFY = "/api/members/university-email/verify";

    @Autowired private WebApplicationContext context;
    @Autowired private JwtProvider jwt;
    @Autowired private UserRepository users;
    @Autowired private UserProfileRepository profiles;
    @Autowired private UniversityEmailVerificationService service;
    @Autowired private UniversityEmailVerificationCodeStore codeStore;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private MailService mail;
    @MockitoSpyBean private PasswordEncoder encoder;
    private MockMvc mvc;
    private User admin;
    private User target;
    private User other;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin = users.saveAndFlush(User.createAdmin("revoke-admin@example.com", "hash", "관리자"));
        target = student("target");
        target.verifyUniversityEmail("revoke-target@uos.ac.kr");
        ReflectionTestUtils.setField(target, "updatedAt", LocalDateTime.of(2020, 1, 1, 0, 0));
        target = users.saveAndFlush(target);
        other = users.saveAndFlush(student("other"));
    }

    @AfterEach
    void cleanUp() {
        pendingStates().clear();
        profiles.deleteAll();
        users.deleteAll();
    }

    @Test
    void fifthWrongAnswerReturns429AndBlocksBothEndpointsWithoutLosingLockoutOnRollback() throws Exception {
        String email = "wrong-limit@uos.ac.kr";
        String correct = issueCode(other, email);
        String wrong = "000000".equals(correct) ? "000001" : "000000";
        for (int i = 0; i < 4; i++) {
            mvc.perform(as(other, verification(email, wrong)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        }
        mvc.perform(as(other, verification(email, wrong)).header("Origin", "http://localhost:3000"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value(429))
                .andExpect(header().string("Retry-After", "1800"))
                .andExpect(header().string("Access-Control-Expose-Headers", org.hamcrest.Matchers.containsString("Retry-After")));
        // Each HTTP transaction rolls back on the exception, but security state must survive it.
        mvc.perform(as(other, verification(email, correct)))
                .andExpect(status().isTooManyRequests());
        mvc.perform(as(other, post(SEND)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"universityEmail\":\"changed-after-wrong@uos.ac.kr\"}"))
                .andExpect(status().isTooManyRequests());
        mvc.perform(as(admin, delete(path(other)))).andExpect(status().isNoContent());
        mvc.perform(as(other, verification(email, correct))).andExpect(status().isTooManyRequests());
        verify(mail, times(1)).sendUniversityVerificationCode(anyString(), anyString());
        User stored = users.findById(other.getId()).orElseThrow();
        assertThat(stored.getUniversityVerifiedAt()).isNull();
        assertThat(stored.getUniversityEmail()).isNull();
        issueCode(target, "unaffected-after-wrong@uos.ac.kr");
    }

    @Test
    void malformedCodeDoesNotConsumeFiveWrongGuesses() throws Exception {
        String email = "malformed-code@uos.ac.kr";
        String correct = issueCode(other, email);
        for (int i = 0; i < 6; i++) {
            mvc.perform(as(other, verification(email, "12345x")))
                    .andExpect(status().isBadRequest());
        }
        String wrong = "000000".equals(correct) ? "000001" : "000000";
        for (int i = 0; i < 4; i++) {
            mvc.perform(as(other, verification(email, wrong))).andExpect(status().isBadRequest());
        }
        mvc.perform(as(other, verification(email, correct))).andExpect(status().isOk());
        assertThat(users.findById(other.getId()).orElseThrow().getUniversityEmail()).isEqualTo(email);
    }

    @Test
    void verificationUsesAccountWideFailureBudgetAcrossEmailsAndResends() throws Exception {
        String email = "before-resend-wrong@uos.ac.kr";
        String correct = issueCode(other, email);
        String wrong = "000000".equals(correct) ? "000001" : "000000";
        for (int i = 0; i < 4; i++) {
            mvc.perform(as(other, verification(email, wrong))).andExpect(status().isBadRequest());
        }
        String changedEmail = "after-resend-wrong@uos.ac.kr";
        String changedCode = issueCode(other, changedEmail);
        String changedWrong = "000000".equals(changedCode) ? "000001" : "000000";
        mvc.perform(as(other, verification(changedEmail, changedWrong)))
                .andExpect(status().isTooManyRequests());
        mvc.perform(as(other, verification(email, correct))).andExpect(status().isTooManyRequests());
    }

    @Test
    void sixthSendReturns429AndLastCodeStillVerifiesWhileOtherUserCanSend() throws Exception {
        String email = "rate-limit@uos.ac.kr";
        for (int i = 0; i < 5; i++) {
            mvc.perform(as(other, post(SEND)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"universityEmail\":\"" + email + "\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(as(other, post(SEND)).contentType(MediaType.APPLICATION_JSON)
                        .header("Origin", "http://localhost:3000")
                        .content("{\"universityEmail\":\"changed-rate-limit@uos.ac.kr\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1800"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Expose-Headers", org.hamcrest.Matchers.containsString("Retry-After")))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(429));
        ArgumentCaptor<String> codes = ArgumentCaptor.forClass(String.class);
        verify(mail, times(5)).sendUniversityVerificationCode(eq(email), codes.capture());
        mvc.perform(as(other, verification(email, codes.getAllValues().getLast())))
                .andExpect(status().isOk());
        assertThat(users.findById(other.getId()).orElseThrow().getUniversityEmail()).isEqualTo(email);
        issueCode(target, "independent-rate-limit@uos.ac.kr");
        // Successful verification and admin revocation must not reset the send budget.
        mvc.perform(as(admin, delete(path(other)))).andExpect(status().isNoContent());
        mvc.perform(as(other, post(SEND)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"universityEmail\":\"" + email + "\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void sendStillRejectsAnonymousAndMalformedEmailWithoutSendingMail() throws Exception {
        mvc.perform(post(SEND).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"universityEmail\":\"test@uos.ac.kr\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(as(other, post(SEND)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"universityEmail\":\"test@example.com\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(mail);
    }

    @Test
    void revokesBothFieldsAndExistingJwtImmediatelyFacesUnverifiedPolicy() throws Exception {
        String targetToken = jwt.generateAccessToken(target);
        mvc.perform(withToken(targetToken, get(STATUS)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.universityVerified").value(true));
        mvc.perform(withToken(targetToken, get("/api/userprofile/me/profile")))
                .andExpect(status().isOk());

        mvc.perform(as(admin, delete(path(target)))).andExpect(status().isNoContent())
                .andExpect(content().string(""));

        User stored = users.findById(target.getId()).orElseThrow();
        assertThat(stored.getUniversityEmail()).isNull();
        assertThat(stored.getUniversityVerifiedAt()).isNull();
        assertThat(stored.getUpdatedAt()).isAfter(LocalDateTime.of(2020, 1, 1, 0, 0));
        assertThat(stored.getEmail()).isEqualTo(target.getEmail());
        assertThat(stored.getNickname()).isEqualTo(target.getNickname());
        assertThat(stored.getOauthProviderId()).isEqualTo(target.getOauthProviderId());
        assertThat(stored.getRole()).isEqualTo(User.Role.STUDENT);
        assertThat(stored.getStatus()).isEqualTo(User.Status.ACTIVE);

        mvc.perform(withToken(targetToken, get(STATUS)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.universityVerified").value(false));
        mvc.perform(withToken(targetToken, get("/api/userprofile/me/profile")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        mvc.perform(withToken(targetToken, get("/api/competitions"))).andExpect(status().isOk());
    }

    @Test
    void repeatedRevocationIsIdempotentAndPreservesOtherUserAndProfile() throws Exception {
        profiles.saveAndFlush(UserProfile.create(target, UserProfile.DepartmentType.COMPUTER_SCIENCE,
                UserProfile.ActivityStatus.RESTING, (short) 2, UserProfile.Gender.MALE,
                null, "보존할 소개", false, "[\"DEVELOPMENT\"]"));
        other.verifyUniversityEmail("revoke-other@uos.ac.kr");
        users.saveAndFlush(other);

        mvc.perform(as(admin, delete(path(target)))).andExpect(status().isNoContent());
        LocalDateTime updatedAt = users.findById(target.getId()).orElseThrow().getUpdatedAt();
        mvc.perform(as(admin, delete(path(target)))).andExpect(status().isNoContent());

        User stored = users.findById(target.getId()).orElseThrow();
        assertThat(stored.getUniversityVerifiedAt()).isNull();
        assertThat(stored.getUniversityEmail()).isNull();
        assertThat(stored.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(profiles.findById(target.getId()).orElseThrow().getBio()).isEqualTo("보존할 소개");
        assertThat(users.findById(other.getId()).orElseThrow().getUniversityEmail())
                .isEqualTo("revoke-other@uos.ac.kr");
    }

    @Test
    void rejectsAnonymousAndVerifiedOrUnverifiedOrdinaryUsers() throws Exception {
        mvc.perform(delete(path(target))).andExpect(status().isUnauthorized());
        mvc.perform(as(target, delete(path(target)))).andExpect(status().isForbidden());
        mvc.perform(as(other, delete(path(target)))).andExpect(status().isForbidden());
        assertThat(users.findById(target.getId()).orElseThrow().getUniversityVerifiedAt()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "SUSPENDED", "DELETED"})
    void staleAdminJwtCannotBypassCurrentDatabasePrivileges(String changed) throws Exception {
        String oldToken = jwt.generateAccessToken(admin);
        if ("STUDENT".equals(changed)) {
            ReflectionTestUtils.setField(admin, "role", User.Role.STUDENT);
        } else {
            ReflectionTestUtils.setField(admin, "status", User.Status.valueOf(changed));
        }
        users.saveAndFlush(admin);
        mvc.perform(withToken(oldToken, delete(path(target)))).andExpect(status().isForbidden());
        assertThat(users.findById(target.getId()).orElseThrow().getUniversityVerifiedAt()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "not-a-number", "99999999999999999999999"})
    void invalidIdReturns400WithoutChangingOtherUsers(String id) throws Exception {
        mvc.perform(as(admin, delete("/api/admin/users/" + id + "/university-verification")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        assertThat(users.findById(target.getId()).orElseThrow().getUniversityVerifiedAt()).isNotNull();
    }

    @Test
    void missingUserReturns404() throws Exception {
        mvc.perform(as(admin, delete("/api/admin/users/" + Long.MAX_VALUE + "/university-verification")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void invalidatesAllTargetCodesButKeepsOtherUserCodesAndSameNumericPrefix() throws Exception {
        String first = issueCode(target, "revoke-pending-one@uos.ac.kr");
        String second = issueCode(target, "revoke-pending-two@uos.ac.kr");
        String otherCode = issueCode(other, "revoke-other-pending@uos.ac.kr");
        // A different user's key must not be removed just because its ID starts with the target ID.
        Long sameNumericPrefix = Long.parseLong(target.getId() + "0");
        codeStore.issue(sameNumericPrefix, "prefix-other@uos.ac.kr");

        mvc.perform(as(admin, delete(path(target)))).andExpect(status().isNoContent());
        mvc.perform(as(target, verification("revoke-pending-one@uos.ac.kr", first)))
                .andExpect(status().isBadRequest());
        mvc.perform(as(target, verification("revoke-pending-two@uos.ac.kr", second)))
                .andExpect(status().isBadRequest());
        assertThat(pendingStates()).containsKey(sameNumericPrefix);
        mvc.perform(as(other, verification("revoke-other-pending@uos.ac.kr", otherCode)))
                .andExpect(status().isOk());
    }

    @Test
    void alreadyUnverifiedUserAlsoLosesPendingCodes() throws Exception {
        String code = issueCode(other, "revoke-unverified-pending@uos.ac.kr");
        mvc.perform(as(admin, delete(path(other)))).andExpect(status().isNoContent());
        mvc.perform(as(other, verification("revoke-unverified-pending@uos.ac.kr", code)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void newCodeCanReverifyTheSameReleasedEmailAndWrongCodeDoesNotConsumeIt() throws Exception {
        mvc.perform(as(admin, delete(path(target)))).andExpect(status().isNoContent());
        String code = issueCode(target, "revoke-target@uos.ac.kr");
        String wrongCode = "000000".equals(code) ? "000001" : "000000";
        mvc.perform(as(target, verification("revoke-target@uos.ac.kr", wrongCode)))
                .andExpect(status().isBadRequest());
        mvc.perform(as(target, verification("revoke-target@uos.ac.kr", code))).andExpect(status().isOk());
        mvc.perform(as(target, get(STATUS)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.universityVerified").value(true));
        mvc.perform(as(target, get("/api/userprofile/me/profile"))).andExpect(status().isOk());
    }

    @Test
    void revokingAnAdminDoesNotRemoveRoleOrExistingSchoolVerificationException() throws Exception {
        admin.verifyUniversityEmail("revoke-admin@uos.ac.kr");
        users.saveAndFlush(admin);
        mvc.perform(as(admin, delete(path(admin)))).andExpect(status().isNoContent());
        assertThat(users.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(User.Role.ADMIN);
        mvc.perform(as(admin, get(STATUS)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.universityVerified").value(false));
        mvc.perform(as(admin, get("/api/userprofile/me/profile"))).andExpect(status().isOk());
    }

    @Test
    void databaseRollbackPreservesVerificationButConservativelyInvalidatesPendingCodes() throws Exception {
        String code = issueCode(target, "revoke-rollback-pending@uos.ac.kr");
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.revokeUniversityVerification(admin.getId(), target.getId());
            throw new IllegalStateException("synthetic rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(users.findById(target.getId()).orElseThrow().getUniversityEmail())
                .isEqualTo("revoke-target@uos.ac.kr");
        mvc.perform(as(target, verification("revoke-rollback-pending@uos.ac.kr", code)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verificationAlreadyInProgressCommitsBeforeLaterRevocation() throws Exception {
        String code = issueCode(target, "revoke-racing@uos.ac.kr");
        CountDownLatch checkingCode = new CountDownLatch(1);
        CountDownLatch finishVerification = new CountDownLatch(1);
        CountDownLatch revokeStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            checkingCode.countDown();
            if (!finishVerification.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test verification release timed out");
            }
            return invocation.callRealMethod();
        }).when(encoder).matches(anyString(), anyString());

        try (var executor = Executors.newFixedThreadPool(2)) {
            var verifyFuture = executor.submit(() -> mvc.perform(
                    as(target, verification("revoke-racing@uos.ac.kr", code))).andReturn().getResponse().getStatus());
            try {
                assertThat(checkingCode.await(5, TimeUnit.SECONDS)).isTrue();
                var revokeFuture = executor.submit(() -> {
                    revokeStarted.countDown();
                    return mvc.perform(as(admin, delete(path(target)))).andReturn().getResponse().getStatus();
                });
                assertThat(revokeStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> revokeFuture.get(150, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                finishVerification.countDown();
                assertThat(verifyFuture.get(15, TimeUnit.SECONDS)).isEqualTo(200);
                assertThat(revokeFuture.get(15, TimeUnit.SECONDS)).isEqualTo(204);
            } finally {
                finishVerification.countDown();
            }
        }
        User stored = users.findById(target.getId()).orElseThrow();
        assertThat(stored.getUniversityEmail()).isNull();
        assertThat(stored.getUniversityVerifiedAt()).isNull();
    }

    private User student(String suffix) {
        return User.createOAuthUser("revoke-" + suffix + "@example.com", "학생-" + suffix,
                User.OAuthProvider.KAKAO, "revoke-" + suffix);
    }

    private String issueCode(User user, String email) throws Exception {
        mvc.perform(as(user, post(SEND)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"universityEmail\":\"" + email + "\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mail).sendUniversityVerificationCode(eq(email), code.capture());
        return code.getValue();
    }

    private MockHttpServletRequestBuilder verification(String email, String code) {
        return post(VERIFY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"universityEmail\":\"" + email + "\",\"code\":\"" + code + "\"}");
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Object> pendingStates() {
        return (Map<Long, Object>) ReflectionTestUtils.getField(codeStore, "states");
    }

    private String path(User user) {
        return "/api/admin/users/" + user.getId() + "/university-verification";
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
