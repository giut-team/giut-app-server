package com.giut.server.profile.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.repository.ProfileRecommendationRepository;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_context;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@Transactional
class ProfileRecommendationPersistenceTest {
    @Autowired private UserRepository users;
    @Autowired private UserProfileRepository profiles;
    @Autowired private ProfileRecommendationRepository recommendations;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private EntityManager entityManager;
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;
    private User viewer;
    private User target;

    @BeforeEach
    void setUp() {
        viewer = user("recommend-viewer", true);
        target = user("recommend-target", true);
        profiles.saveAndFlush(UserProfile.create(target, UserProfile.DepartmentType.COMPUTER_SCIENCE,
                UserProfile.ActivityStatus.LOOKING_FOR_TEAM, (short) 3, null, null, "소개", true, "[]"));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void recommendationsPersistWithoutDuplicatesAndOnlyTheActorCanCancelTheirs() throws Exception {
        String path = "/api/profile/" + target.getId() + "/recommendations";
        mvc.perform(asUser(post(path), viewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recommended").value(true))
                .andExpect(jsonPath("$.recommendationCount").value(1));
        mvc.perform(asUser(post(path), viewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recommendationCount").value(1));
        entityManager.flush();
        entityManager.clear();
        assertThat(recommendations.countByRecommendedUser_Id(target.getId())).isEqualTo(1);
        mvc.perform(asUser(get(path), viewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recommended").value(true));

        User observer = user("recommend-observer", true);
        mvc.perform(asUser(get(path), observer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recommended").value(false))
                .andExpect(jsonPath("$.recommendationCount").value(1));
        mvc.perform(asUser(delete(path), observer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recommendationCount").value(1));
        assertThat(recommendations.existsByRecommender_IdAndRecommendedUser_Id(viewer.getId(), target.getId())).isTrue();

        mvc.perform(asUser(delete(path), viewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recommended").value(false))
                .andExpect(jsonPath("$.recommendationCount").value(0));
        mvc.perform(asUser(delete(path), viewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recommendationCount").value(0));
        entityManager.flush();
        entityManager.clear();
        assertThat(recommendations.countByRecommendedUser_Id(target.getId())).isZero();
    }

    @Test
    void retiredScrapRouteAndJpaEntityAreNotRegistered() throws Exception {
        var mappings = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        assertThat(mappings.getHandlerMethods().keySet().stream().flatMap(info -> info.getPatternValues().stream()))
                .doesNotContain("/api/profile/{userId}/scrap")
                .contains("/api/profile/{userId}/recommendations", "/api/profile/shares/{token}/target");
        assertThat(entityManager.getMetamodel().getEntities().stream().map(type -> type.getName()))
                .doesNotContain("ProfileScrap")
                .contains("ProfileRecommendation", "CompetitionScrap", "TeamScrap");

        String path = "/api/profile/" + target.getId() + "/scrap";
        for (HttpMethod method : new HttpMethod[]{HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE}) {
            mvc.perform(asUser(request(method, path), viewer))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
        }
    }

    @Test
    void recommendationStillRequiresLoginAndSchoolVerification() throws Exception {
        String path = "/api/profile/" + target.getId() + "/recommendations";
        User unverified = user("recommend-unverified", false);
        for (HttpMethod method : new HttpMethod[]{HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE}) {
            mvc.perform(request(method, path).servletPath(path)).andExpect(status().isUnauthorized());
            mvc.perform(asUser(request(method, path), unverified)).andExpect(status().isForbidden());
        }
        assertThat(recommendations.count()).isZero();
    }

    @Test
    void recommendationStillRejectsSelfAndHiddenProfiles() throws Exception {
        mvc.perform(asUser(post("/api/profile/" + viewer.getId() + "/recommendations"), viewer))
                .andExpect(status().isBadRequest());
        var profile = profiles.findById(target.getId()).orElseThrow();
        org.springframework.test.util.ReflectionTestUtils.setField(profile, "searchable", false);
        profiles.flush();
        mvc.perform(asUser(post("/api/profile/" + target.getId() + "/recommendations"), viewer))
                .andExpect(status().isNotFound());
        assertThat(recommendations.count()).isZero();
    }

    private User user(String name, boolean verified) {
        User user = User.createOAuthUser(name + "@example.com", name, User.OAuthProvider.KAKAO, name);
        if (verified) {
            user.verifyUniversityEmail(name + "@uos.ac.kr");
        }
        return users.saveAndFlush(user);
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request, User user) {
        return request.header("Authorization", "Bearer " + jwtProvider.generateAccessToken(user))
                .with(servletRequest -> {
                    servletRequest.setServletPath(servletRequest.getRequestURI());
                    return servletRequest;
                });
    }
}
