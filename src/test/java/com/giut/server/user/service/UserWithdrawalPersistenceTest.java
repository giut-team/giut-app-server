package com.giut.server.user.service;

import com.giut.server.auth.exception.AuthenticationFailedException;
import com.giut.server.auth.security.AppleJwtValidator;
import com.giut.server.auth.security.JwtProvider;
import com.giut.server.auth.service.AppleService;
import com.giut.server.auth.service.AppleTokenClient;
import com.giut.server.chat.dto.response.ChatMessageResponse;
import com.giut.server.chat.entity.*;
import com.giut.server.chat.event.ChatMessageBroadcaster;
import com.giut.server.chat.event.ChatMessageEvent;
import com.giut.server.chat.repository.*;
import com.giut.server.chat.security.ChatSubscriptionInterceptor;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.global.exception.ConflictException;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.repository.ProfileShareLinkRepository;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.profile.service.ProfileShareLinkService;
import com.giut.server.team.entity.Team;
import com.giut.server.team.entity.TeamMember;
import com.giut.server.team.repository.TeamMemberRepository;
import com.giut.server.team.repository.TeamRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real security/MVC/service commits in a class-specific H2 DB. No shared DB or external OAuth calls. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_withdrawal;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "jwt.expiration-time=3600000", "jwt.refresh-expiration-time=1209600000",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL=",
        "DISCORD_PROFILE_REPORT_WEBHOOK_URL=", "DISCORD_SLOW_API_ENABLED=false",
        "DISCORD_PROFILE_REPORT_ENABLED=false", "app.oauth.cookie-secure=true",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
class UserWithdrawalPersistenceTest {
    private static final String PATH = "/api/users/me";
    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;
    @Autowired private UserProfileRepository profiles;
    @Autowired private ProfileShareLinkRepository links;
    @Autowired private ProfileShareLinkService sharing;
    @Autowired private CompetitionRepository competitions;
    @Autowired private TeamRepository teams;
    @Autowired private TeamMemberRepository teamMembers;
    @Autowired private ChatRoomRepository rooms;
    @Autowired private ChatRoomMemberRepository roomMembers;
    @Autowired private ChatMessageRepository messages;
    @Autowired private ChatMessageBroadcaster broadcaster;
    @Autowired private ChatSubscriptionInterceptor subscriptions;
    @Autowired private JwtProvider jwt;
    @Autowired private UserWithdrawalService service;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AppleService apple;
    @MockitoBean private AppleJwtValidator appleValidator;
    @MockitoBean private AppleTokenClient appleTokens;
    @MockitoBean private SimpMessagingTemplate messaging;
    private MockMvc mvc;
    private User target;
    private User viewer;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.execute("CREATE ALIAS IF NOT EXISTS JSON_QUOTE FOR 'com.giut.server.profile.support.H2ProfileJsonFunctions.jsonQuote'");
        jdbc.execute("CREATE ALIAS IF NOT EXISTS JSON_CONTAINS FOR 'com.giut.server.profile.support.H2ProfileJsonFunctions.jsonContains'");
        target = student("target");
        viewer = student("viewer");
    }

    @AfterEach
    void cleanUp() {
        messages.deleteAll(); roomMembers.deleteAll(); rooms.deleteAll();
        teamMembers.deleteAll(); teams.deleteAll(); competitions.deleteAll();
        links.deleteAll(); profiles.deleteAll(); users.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void withdrawsSelfWithOrWithoutSchoolVerificationAndPreservesPersonalData(boolean verified) throws Exception {
        if (!verified) { target.revokeUniversityVerification(); target = users.saveAndFlush(target); }
        LocalDateTime before = target.getUpdatedAt();
        String token = jwt.generateAccessToken(target);
        MockHttpServletRequestBuilder request = delete(PATH).param("userId", viewer.getId().toString());
        if (verified) request.cookie(new Cookie("giut_access_token", token));
        else request.header("Authorization", "Bearer " + token);
        var result = mvc.perform(path(request)).andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).hasSize(2).allSatisfy(cookie ->
                assertThat(cookie).contains("Max-Age=0", "Path=/", "HttpOnly", "Secure", "SameSite=None"));
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(cookie -> assertThat(cookie).startsWith("giut_access_token="))
                .anySatisfy(cookie -> assertThat(cookie).startsWith("giut_refresh_token="));

        User stored = users.findById(target.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(User.Status.DELETED);
        assertThat(stored.getUpdatedAt()).isAfterOrEqualTo(before);
        assertThat(stored.getEmail()).isEqualTo(target.getEmail());
        assertThat(stored.getNickname()).isEqualTo(target.getNickname());
        assertThat(stored.getOauthProviderId()).isEqualTo(target.getOauthProviderId());
        assertThat(stored.getUniversityEmail()).isEqualTo(target.getUniversityEmail());
        assertThat(stored.getUniversityVerifiedAt()).isEqualTo(target.getUniversityVerifiedAt());
        assertThat(stored.getCreatedAt()).isEqualTo(target.getCreatedAt());
        assertThat(profiles.findById(target.getId()).orElseThrow().getBio()).isEqualTo("보존할 소개");
        assertThat(users.findById(viewer.getId()).orElseThrow().getStatus()).isEqualTo(User.Status.ACTIVE);
        assertThat(users.count()).isEqualTo(2);
    }

    @Test
    void oldBearerAndCookieCannotReachApisAndRepeatedWithdrawalDoesNotWriteAgain() throws Exception {
        String token = jwt.generateAccessToken(target);
        mvc.perform(as(target, delete(PATH))).andExpect(status().isNoContent());
        LocalDateTime deletedAt = users.findById(target.getId()).orElseThrow().getUpdatedAt();
        for (String route : new String[]{"/api/userprofile/me/profile", "/api/competitions",
                "/api/members/university-email/status", "/api/chat-rooms", "/api/profile/shares/any-token"}) {
            mvc.perform(path(get(route)).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
            mvc.perform(path(get(route)).cookie(new Cookie("giut_access_token", token)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(path(delete(PATH)).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        assertThat(users.findById(target.getId()).orElseThrow().getUpdatedAt()).isEqualTo(deletedAt);
        mvc.perform(path(get("/actuator/health"))).andExpect(status().isOk());
    }

    @Test
    void anonymousInvalidAndRefreshTokensCannotWithdraw() throws Exception {
        mvc.perform(path(delete(PATH))).andExpect(status().isUnauthorized());
        mvc.perform(path(delete(PATH)).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(path(delete(PATH)).header("Authorization", "Bearer " + jwt.generateRefreshToken(target)))
                .andExpect(status().isUnauthorized());
        assertThat(users.findById(target.getId()).orElseThrow().getStatus()).isEqualTo(User.Status.ACTIVE);
    }

    @Test
    void administratorCannotWithdrawEvenWhenNoTeamsExist() throws Exception {
        User admin = users.saveAndFlush(User.createAdmin("admin@example.com", "hash", "관리자"));
        mvc.perform(as(admin, delete(PATH))).andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(users.findById(admin.getId()).orElseThrow().getStatus()).isEqualTo(User.Status.ACTIVE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECRUITING", "CLOSED", "ARCHIVED"})
    void leaderMustArchiveEveryTeamBeforeWithdrawal(String status) throws Exception {
        Team team = team(target);
        ReflectionTestUtils.setField(team, "status", Team.Status.valueOf(status)); teams.saveAndFlush(team);
        if (status.equals("ARCHIVED")) {
            mvc.perform(as(target, delete(PATH))).andExpect(status().isNoContent());
        } else {
            mvc.perform(as(target, delete(PATH))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409)).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
            assertThat(users.findById(target.getId()).orElseThrow().getStatus()).isEqualTo(User.Status.ACTIVE);
            team.archive(); teams.saveAndFlush(team);
            mvc.perform(as(target, delete(PATH))).andExpect(status().isNoContent());
        }
        assertThat(teams.count()).isEqualTo(1);
    }

    @Test
    void withdrawnProfileDisappearsFromPublicListDetailAndPreviouslyIssuedShares() throws Exception {
        String share = "/api/profile/shares/" + sharing.createLink(target.getId()).token();
        mvc.perform(as(viewer, get("/api/profile"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(path(get(share))).andExpect(status().isOk());
        mvc.perform(as(target, delete(PATH))).andExpect(status().isNoContent());
        mvc.perform(as(viewer, get("/api/profile"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.profiles").isEmpty());
        mvc.perform(as(viewer, get("/api/profile/" + target.getId()))).andExpect(status().isNotFound());
        mvc.perform(path(get(share))).andExpect(status().isNotFound());
        mvc.perform(as(viewer, get(share + "/target"))).andExpect(status().isNotFound());
        assertThat(profiles.existsById(target.getId())).isTrue();
        assertThat(links.count()).isEqualTo(1);
    }

    @Test
    void memberHistoryAndChatRecordsRemainButAccountCannotSubscribeOrReceiveNewMessages() throws Exception {
        Team team = team(viewer);
        TeamMember membership = teamMembers.saveAndFlush(TeamMember.createMember(team.getId(), target.getId(), "BACKEND"));
        ChatRoom room = rooms.saveAndFlush(ChatRoom.createPersonalRoom());
        roomMembers.saveAndFlush(ChatRoomMember.join(room.getId(), target.getId()));
        roomMembers.saveAndFlush(ChatRoomMember.join(room.getId(), viewer.getId()));
        ChatMessage message = messages.saveAndFlush(ChatMessage.createText(room.getId(), viewer.getId(), "보존할 메시지"));
        var connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.setUser(() -> target.getId().toString());
        var frame = MessageBuilder.createMessage(new byte[0], connect.getMessageHeaders());
        assertThatCode(() -> subscriptions.preSend(frame, null)).doesNotThrowAnyException();
        mvc.perform(as(target, delete(PATH))).andExpect(status().isNoContent());

        assertThatThrownBy(() -> subscriptions.preSend(frame, null)).isInstanceOf(AccessDeniedException.class);
        ChatMessageResponse response = ChatMessageResponse.from(message);
        broadcaster.broadcast(new ChatMessageEvent(response));
        verify(messaging).convertAndSendToUser(viewer.getId().toString(), "/queue/chat-rooms/" + room.getId(), response);
        verify(messaging, never()).convertAndSendToUser(target.getId().toString(), "/queue/chat-rooms/" + room.getId(), response);
        assertThat(teamMembers.findById(membership.getId()).orElseThrow().getStatus()).isEqualTo(TeamMember.Status.ACTIVE);
        assertThat(roomMembers.findByChatRoomIdAndUserId(room.getId(), target.getId()).orElseThrow().getLeftAt()).isNull();
        assertThat(messages.findById(message.getId()).orElseThrow().getContent()).isEqualTo("보존할 메시지");
    }

    @Test
    void accountWithoutProfileCanAlsoWithdraw() throws Exception {
        profiles.deleteById(target.getId());
        mvc.perform(as(target, delete(PATH))).andExpect(status().isNoContent());
        assertThat(users.findById(target.getId()).orElseThrow().getStatus()).isEqualTo(User.Status.DELETED);
    }

    @Test
    void socialLoginCannotRestoreWithdrawnAccountOrCreateADuplicate() throws Exception {
        ReflectionTestUtils.setField(target, "oauthProvider", User.OAuthProvider.APPLE); users.saveAndFlush(target);
        mvc.perform(as(target, delete(PATH))).andExpect(status().isNoContent());
        Map<String, Object> claims = Map.of("sub", target.getOauthProviderId(), "email", target.getEmail());
        when(appleValidator.validateAndGetClaims("callback-token", "nonce")).thenReturn(claims);
        when(appleTokens.exchangeAuthorizationCode("code")).thenReturn(new AppleTokenClient.AppleTokenResponse("exchange-token"));
        when(appleValidator.validateAndGetClaims("exchange-token")).thenReturn(claims);
        when(appleValidator.extractAppleUserId(claims)).thenReturn(target.getOauthProviderId());
        when(appleValidator.extractEmail(claims)).thenReturn(target.getEmail());
        assertThatThrownBy(() -> apple.loginWithApple("code", "callback-token", "이름", "nonce"))
                .isInstanceOf(AuthenticationFailedException.class);
        assertThat(users.count()).isEqualTo(2);
        assertThat(users.findById(target.getId()).orElseThrow().getStatus()).isEqualTo(User.Status.DELETED);
    }

    @Test
    void withdrawalWaitsForTheUserLockAndSeesAConcurrentlyCreatedTeam() throws Exception {
        Competition competition = competition();
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var creation = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                User leader = users.findByIdForUpdate(target.getId()).orElseThrow();
                locked.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("lock test timed out"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return teams.saveAndFlush(newTeam(competition, leader)).getId();
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var withdrawal = executor.submit(() -> service.withdraw(target.getId()));
            assertThatThrownBy(() -> withdrawal.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); creation.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> withdrawal.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(ConflictException.class);
            assertThat(users.findById(target.getId()).orElseThrow().getStatus()).isEqualTo(User.Status.ACTIVE);
        } finally { release.countDown(); executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS); }
    }

    private User student(String name) {
        User user = User.createOAuthUser(name + "@example.com", name, User.OAuthProvider.KAKAO, "withdraw-" + name);
        user.verifyUniversityEmail(name + "@uos.ac.kr");
        user = users.saveAndFlush(user);
        profiles.saveAndFlush(UserProfile.create(user, UserProfile.DepartmentType.COMPUTER_SCIENCE,
                UserProfile.ActivityStatus.LOOKING_FOR_TEAM, (short) 3, null, null, "보존할 소개", true, "[\"DEVELOPMENT\"]"));
        jdbc.update("UPDATE user_profiles SET primary_roles = JSON '[\"DEVELOPMENT\"]' WHERE user_id = ?", user.getId());
        return users.findById(user.getId()).orElseThrow();
    }

    private Competition competition() {
        return competitions.saveAndFlush(Competition.create("탈퇴 검증 공모전", Competition.Category.WEB_MOBILE_IT,
                null, null, "소개", Instant.now(), Instant.now().plusSeconds(86400),
                Competition.PublicationStatus.PUBLISHED, Competition.VerificationStatus.MANUALLY_VERIFIED, null));
    }
    private Team team(User leader) { return teams.saveAndFlush(newTeam(competition(), leader)); }
    private Team newTeam(Competition competition, User leader) {
        return Team.create(competition, leader, "기존 팀", "소개", Team.ActivityMode.ONLINE,
                (short) 5, (short) 1, Team.MeetingPlace.ANYWHERE);
    }
    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return path(request).header("Authorization", "Bearer " + jwt.generateAccessToken(user));
    }
    private MockHttpServletRequestBuilder path(MockHttpServletRequestBuilder request) {
        return request.with(servlet -> { servlet.setServletPath(servlet.getRequestURI()); return servlet; });
    }
}
