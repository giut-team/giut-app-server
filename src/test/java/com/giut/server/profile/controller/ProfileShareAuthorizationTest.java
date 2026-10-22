package com.giut.server.profile.controller;

import com.giut.server.auth.security.JwtAccessDeniedHandler;
import com.giut.server.auth.security.JwtAuthenticationEntryPoint;
import com.giut.server.auth.security.JwtProvider;
import com.giut.server.global.alert.DiscordAlertService;
import com.giut.server.global.config.SecurityConfig;
import com.giut.server.global.exception.GlobalExceptionHandler;
import com.giut.server.profile.dto.response.SharedProfileResponse;
import com.giut.server.profile.entity.ProfileShareLink;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.repository.ProfileShareLinkRepository;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.profile.service.ProfileShareLinkService;
import com.giut.server.profile.service.UserProfileService;
import com.giut.server.user.entity.User;
import com.giut.server.user.service.UserBlockService;
import com.giut.server.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProfileShareAuthorizationTest {

    private static final String TOKEN = "A".repeat(43);
    private static final String SHARE_URL = "/api/profile/shares/" + TOKEN;
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private ProfileShareLinkRepository links;
    private UserProfileService profiles;
    private UserRepository users;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testSecurity", Map.of(
                "jwt.secret", Base64.getEncoder().encodeToString(new byte[32]),
                "jwt.expiration-time", "3600000",
                "jwt.refresh-expiration-time", "1209600000"
        )));
        context.register(TestConfig.class);
        context.refresh();
        links = context.getBean(ProfileShareLinkRepository.class);
        profiles = context.getBean(UserProfileService.class);
        users = context.getBean(UserRepository.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"anonymous", "unverified", "unverified-cookie", "verified"})
    void validShareTokenAllowsAllViewersAndReturnsOnlyTheCard(String viewer) throws Exception {
        ProfileShareLink link = link(Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        when(links.findByTokenHash(anyString())).thenReturn(Optional.of(link));
        when(profiles.getSharedProfile(org.mockito.ArgumentMatchers.nullable(Long.class), eq(12L))).thenReturn(new SharedProfileResponse(
                "공유 회원", false, null, UserProfile.ActivityStatus.LOOKING_FOR_TEAM,
                "팀 찾는 중", List.of(), "컴퓨터과학부", (short) 3, "소개", List.of()
        ));

        mockMvc.perform(asViewer(get(SHARE_URL), viewer))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.nickname").value("공유 회원"))
                .andExpect(jsonPath("$.grade").value(3))
                .andExpect(jsonPath("$.portfolios").doesNotExist())
                .andExpect(jsonPath("$.activityHistories").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());

        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(TOKEN.getBytes(StandardCharsets.US_ASCII)));
        verify(links).findByTokenHash(hash);
        verify(profiles).getSharedProfile("anonymous".equals(viewer) ? null : 42L, 12L);
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed", "missing", "expired", "revoked", "over-three-hours"})
    void invalidShareTokensReturn404WithoutLogin(String state) throws Exception {
        if (!"malformed".equals(state)) {
            Optional<ProfileShareLink> found = Optional.empty();
            if (!"missing".equals(state)) {
                Instant createdAt = Instant.now().minus(Duration.ofMinutes(10));
                Instant expiresAt = Instant.now().plus(Duration.ofHours(2));
                if ("expired".equals(state)) {
                    expiresAt = Instant.now().minusSeconds(1);
                }
                if ("over-three-hours".equals(state)) {
                    createdAt = Instant.now().minus(Duration.ofHours(4));
                    expiresAt = Instant.now().plus(Duration.ofDays(30));
                }
                ProfileShareLink link = link(createdAt, expiresAt);
                if ("revoked".equals(state)) {
                    link.revoke(Instant.now());
                }
                found = Optional.of(link);
            }
            when(links.findByTokenHash(anyString())).thenReturn(found);
        }

        mockMvc.perform(get("malformed".equals(state) ? "/api/profile/shares/invalid-token" : SHARE_URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("공유 프로필을 찾을 수 없습니다."));

        verifyNoInteractions(profiles, users, context.getBean(DiscordAlertService.class));
        if ("malformed".equals(state)) {
            verifyNoInteractions(links);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"anonymous", "unverified"})
    void otherProfileReadsAndLinkMutationsRemainProtected(String viewer) throws Exception {
        int expected = "anonymous".equals(viewer) ? 401 : 403;
        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/api/profile"), get("/api/profile/12"), get("/api/profile/tags?type=SKILL"),
                get("/api/profile/shares"), get(SHARE_URL + "/extra"),
                get(SHARE_URL + "/target"), get("/api/profile/12/recommendations"),
                post("/api/profile/12/recommendations"), delete("/api/profile/12/recommendations"),
                get("/api/profile/share/" + TOKEN), post(SHARE_URL), delete(SHARE_URL), head(SHARE_URL),
                post("/api/users/me/profile-share-links"),
                post("/api/users/12/profile-share-links"),
                delete("/api/users/me/profile-share-links/7")
        );
        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(asViewer(request, viewer))
                    .andExpect(status().is(expected))
                    .andExpect(jsonPath("$.code").value(expected));
        }
        verifyNoInteractions(links, profiles);
    }

    @org.junit.jupiter.api.Test
    void verifiedViewerCanResolveTargetButExpiredTokenStillFails() throws Exception {
        when(users.existsByIdAndUniversityVerifiedAtIsNotNull(42L)).thenReturn(true);
        ProfileShareLink validLink = link(Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        when(links.findByTokenHash(anyString())).thenReturn(Optional.of(validLink));
        mockMvc.perform(asViewer(get(SHARE_URL + "/target"), "verified"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.userId").value(12));
        verify(profiles).validatePublicProfile(42L, 12L);
        ProfileShareLink expiredLink = link(Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1));
        when(links.findByTokenHash(anyString())).thenReturn(Optional.of(expiredLink));
        mockMvc.perform(asViewer(get(SHARE_URL + "/target"), "verified"))
                .andExpect(status().isNotFound());
    }

    private ProfileShareLink link(Instant createdAt, Instant expiresAt) {
        UserProfile profile = mock(UserProfile.class);
        when(profile.getUserId()).thenReturn(12L);
        ProfileShareLink link = ProfileShareLink.create(profile, "hash", expiresAt);
        ReflectionTestUtils.setField(link, "createdAt", createdAt);
        return link;
    }

    private MockHttpServletRequestBuilder asViewer(MockHttpServletRequestBuilder request, String viewer) {
        // MockMvc leaves servletPath empty by default; match the app's root DispatcherServlet mapping.
        request.with(servletRequest -> {
            servletRequest.setServletPath(servletRequest.getRequestURI());
            return servletRequest;
        });
        if ("anonymous".equals(viewer)) {
            return request;
        }
        User user = User.createOAuthUser("student@example.com", "학생", User.OAuthProvider.KAKAO, "provider-id");
        ReflectionTestUtils.setField(user, "id", 42L);
        if ("verified".equals(viewer)) {
            user.verifyUniversityEmail("student@uos.ac.kr");
        }
        String jwt = context.getBean(JwtProvider.class).generateAccessToken(user);
        return "unverified-cookie".equals(viewer)
                ? request.cookie(new Cookie("giut_access_token", jwt))
                : request.header("Authorization", "Bearer " + jwt);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class,
            ProfileShareController.class, ProfileShareLinkService.class, GlobalExceptionHandler.class})
    static class TestConfig {
        @Bean JwtProvider jwtProvider() { return new JwtProvider(); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean UserBlockService userBlockService() { return mock(UserBlockService.class); }
        @Bean ProfileShareLinkRepository profileShareLinkRepository() { return mock(ProfileShareLinkRepository.class); }
        @Bean UserProfileRepository userProfileRepository() { return mock(UserProfileRepository.class); }
        @Bean UserProfileService userProfileService() { return mock(UserProfileService.class); }
        @Bean DiscordAlertService discordAlertService() { return mock(DiscordAlertService.class); }
    }
}
