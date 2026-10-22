package com.giut.server.profile.controller;

import com.giut.server.auth.security.JwtAccessDeniedHandler;
import com.giut.server.auth.security.JwtAuthenticationEntryPoint;
import com.giut.server.auth.security.JwtProvider;
import com.giut.server.global.config.SecurityConfig;
import com.giut.server.profile.dto.response.SeedDefaultProfileTagsResponse;
import com.giut.server.profile.dto.response.SeedDefaultProfileTagsResponse.TagTypeResult;
import com.giut.server.profile.service.DefaultProfileTagService;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Base64;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSkillTagControllerTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private JwtProvider jwtProvider;
    private DefaultProfileTagService service;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testJwt", Map.of(
                "jwt.secret", Base64.getEncoder().encodeToString(new byte[32]),
                "jwt.expiration-time", "3600000",
                "jwt.refresh-expiration-time", "1209600000"
        )));
        context.register(TestConfig.class);
        context.refresh();
        jwtProvider = context.getBean(JwtProvider.class);
        service = context.getBean(DefaultProfileTagService.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/profile/tags/defaults", "/api/admin/profile/tags/skills/defaults"})
    void anonymousRequestIsUnauthorizedAndDoesNotSeed(String path) throws Exception {
        mockMvc.perform(post(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/profile/tags/defaults", "/api/admin/profile/tags/skills/defaults"})
    void studentAccessTokenIsForbiddenAndDoesNotSeed(String path) throws Exception {
        String token = jwtProvider.generateAccessToken(user(User.Role.STUDENT));
        when(context.getBean(UserRepository.class).existsByIdAndUniversityVerifiedAtIsNotNull(10L)).thenReturn(true);

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/profile/tags/defaults", "/api/admin/profile/tags/skills/defaults"})
    void adminAccessTokenCanSeedAllTypesWithoutARequestBody(String path) throws Exception {
        String token = jwtProvider.generateAccessToken(user(User.Role.ADMIN));
        when(service.seedDefaults(10L)).thenReturn(new SeedDefaultProfileTagsResponse(40, 0,
                java.util.stream.LongStream.rangeClosed(1, 40).boxed().toList(),
                new TagTypeResult(5, 0, java.util.stream.LongStream.rangeClosed(41, 45).boxed().toList()),
                new TagTypeResult(8, 0, java.util.stream.LongStream.rangeClosed(46, 53).boxed().toList())));

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(40))
                .andExpect(jsonPath("$.existingCount").value(0))
                .andExpect(jsonPath("$.skillTagIds.length()").value(40))
                .andExpect(jsonPath("$.interestTags.createdCount").value(5))
                .andExpect(jsonPath("$.interestTags.tagIds.length()").value(5))
                .andExpect(jsonPath("$.experienceTags.createdCount").value(8))
                .andExpect(jsonPath("$.experienceTags.tagIds.length()").value(8));

        verify(service).seedDefaults(10L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/profile/tags/defaults", "/api/admin/profile/tags/skills/defaults"})
    void adminRefreshTokenCannotSeed(String path) throws Exception {
        String token = jwtProvider.generateRefreshToken(user(User.Role.ADMIN));

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    private User user(User.Role role) {
        User user = User.createAdmin("admin@example.com", "password-hash", "관리자");
        ReflectionTestUtils.setField(user, "id", 10L);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({
            SecurityConfig.class,
            JwtAuthenticationEntryPoint.class,
            JwtAccessDeniedHandler.class,
            AdminSkillTagController.class
    })
    static class TestConfig {

        @Bean
        JwtProvider jwtProvider() {
            return new JwtProvider();
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        DefaultProfileTagService defaultProfileTagService() {
            return mock(DefaultProfileTagService.class);
        }
    }
}
