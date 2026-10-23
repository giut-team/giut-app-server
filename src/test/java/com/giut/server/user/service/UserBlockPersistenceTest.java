package com.giut.server.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giut.server.auth.security.JwtProvider;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.repository.ProfileRecommendationRepository;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.entity.UserBlock;
import com.giut.server.user.repository.UserBlockRepository;
import com.giut.server.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_blocks;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "DISCORD_ALERT_WEBHOOK_URL="
})
@Transactional
class UserBlockPersistenceTest {
    @Autowired private UserRepository users;
    @Autowired private UserProfileRepository profiles;
    @Autowired private UserBlockRepository blocks;
    @Autowired private ProfileRecommendationRepository recommendations;
    @Autowired private JwtProvider jwt;
    @Autowired private EntityManager em;
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;
    private User actor;
    private User target;
    private User other;

    @BeforeEach
    void setUp() {
        // Test-only equivalents let the existing native MySQL query execute under isolated H2.
        em.createNativeQuery("CREATE ALIAS IF NOT EXISTS JSON_QUOTE FOR 'com.giut.server.profile.support.H2ProfileJsonFunctions.jsonQuote'").executeUpdate();
        em.createNativeQuery("CREATE ALIAS IF NOT EXISTS JSON_CONTAINS FOR 'com.giut.server.profile.support.H2ProfileJsonFunctions.jsonContains'").executeUpdate();
        actor = user("block-actor", true);
        target = user("block-target", true);
        other = user("block-other", true);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void repeatedBlocksPersistAndOnlyOwnerCanListOrRemoveThem() throws Exception {
        String path = blockPath(target);
        mvc.perform(as(actor, post(path))).andExpect(status().isOk()).andExpect(jsonPath("$.blocked").value(true));
        mvc.perform(as(actor, post(path))).andExpect(status().isOk());
        em.flush(); em.clear();
        assertThat(blocks.count()).isEqualTo(1);
        mvc.perform(as(actor, get(path))).andExpect(status().isOk()).andExpect(jsonPath("$.blocked").value(true));
        mvc.perform(as(target, get(blockPath(actor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.blockedByTarget").doesNotExist());
        mvc.perform(as(actor, get("/api/users/me/blocks")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.users[0].userId").value(target.getId()))
                .andExpect(jsonPath("$.users[0].blockedAt").isNotEmpty());
        mvc.perform(as(target, get("/api/users/me/blocks")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(as(target, delete(blockPath(actor)))).andExpect(status().isOk());
        assertThat(blocks.existsByBlocker_IdAndBlockedUser_Id(actor.getId(), target.getId())).isTrue();
        mvc.perform(as(actor, delete(path))).andExpect(status().isOk()).andExpect(jsonPath("$.blocked").value(false));
        mvc.perform(as(actor, delete(path))).andExpect(status().isOk());
        assertThat(blocks.count()).isZero();
    }

    @Test
    void hidesBothDirectionsIncludingPortfolioAndRecommendationButKeepsHistory() throws Exception {
        String recommendation = detailPath(target) + "/recommendations";
        mvc.perform(as(actor, post(recommendation))).andExpect(status().isOk());
        mvc.perform(as(actor, post(blockPath(target)))).andExpect(status().isOk());
        em.flush(); em.clear();
        for (var pair : new User[][]{{actor, target}, {target, actor}}) {
            mvc.perform(as(pair[0], get(detailPath(pair[1])))).andExpect(status().isNotFound());
            mvc.perform(as(pair[0], get(detailPath(pair[1]) + "/portfolio-items"))).andExpect(status().isNotFound());
            for (HttpMethod method : new HttpMethod[]{HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE}) {
                mvc.perform(as(pair[0], request(method, detailPath(pair[1]) + "/recommendations")))
                        .andExpect(status().isNotFound());
            }
        }
        mvc.perform(as(other, get(detailPath(target)))).andExpect(status().isOk());
        assertThat(recommendations.existsByRecommender_IdAndRecommendedUser_Id(actor.getId(), target.getId())).isTrue();
        mvc.perform(as(actor, delete(blockPath(target)))).andExpect(status().isOk());
        mvc.perform(as(actor, get(detailPath(target))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recommended").value(true))
                .andExpect(jsonPath("$.recommendationCount").value(1));
    }

    @Test
    void listQueryFiltersBothDirectionsBeforePaginationAndCount() throws Exception {
        mvc.perform(as(actor, post(blockPath(target)))).andExpect(status().isOk());
        mvc.perform(as(other, post(blockPath(actor)))).andExpect(status().isOk());
        User visible = user("block-visible", true);
        em.flush(); em.clear();

        var result = profiles.findPublicProfiles("RESTING", "ACTIVE", actor.getId(), "DEVELOPMENT",
                null, null, null, null, null, PageRequest.of(0, 1));
        assertThat(result.getContent()).extracting(UserProfile::getUserId).containsExactly(visible.getId());
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        var next = profiles.findPublicProfiles("RESTING", "ACTIVE", actor.getId(), "DEVELOPMENT",
                null, null, null, null, null, PageRequest.of(1, 1));
        assertThat(next.getContent()).isEmpty();
        assertThat(next.getTotalElements()).isEqualTo(1);
        mvc.perform(as(actor, get("/api/profile").param("primaryRole", "DEVELOPMENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.profiles[0].userId").value(visible.getId()));
    }

    @Test
    void mutualBlocksRequireEachOwnerToUnblockAndInactiveTargetCanBeRemoved() throws Exception {
        mvc.perform(as(actor, post(blockPath(target)))).andExpect(status().isOk());
        mvc.perform(as(target, post(blockPath(actor)))).andExpect(status().isOk());
        mvc.perform(as(actor, delete(blockPath(target)))).andExpect(status().isOk());
        mvc.perform(as(actor, get(detailPath(target)))).andExpect(status().isNotFound());
        mvc.perform(as(target, delete(blockPath(actor)))).andExpect(status().isOk());
        mvc.perform(as(actor, get(detailPath(target)))).andExpect(status().isOk());
        mvc.perform(as(actor, post(blockPath(target)))).andExpect(status().isOk());
        ReflectionTestUtils.setField(users.findById(target.getId()).orElseThrow(), "status", User.Status.DELETED);
        users.flush();
        mvc.perform(as(actor, delete(blockPath(target)))).andExpect(status().isOk());
        assertThat(blocks.count()).isZero();
    }

    @Test
    void sharedCardsRespectLoggedInBlocksWhileAnonymousTokenPolicyStaysUnchanged() throws Exception {
        var issued = mvc.perform(as(target, post("/api/users/me/profile-share-links")))
                .andExpect(status().isCreated()).andReturn();
        String token = new ObjectMapper().readTree(issued.getResponse().getContentAsString()).path("token").asText();
        mvc.perform(as(actor, post(blockPath(target)))).andExpect(status().isOk());
        String path = "/api/profile/shares/" + token;
        mvc.perform(as(actor, get(path))).andExpect(status().isNotFound());
        mvc.perform(as(actor, get(path + "/target"))).andExpect(status().isNotFound());
        mvc.perform(as(actor, post("/api/users/" + target.getId() + "/profile-share-links")))
                .andExpect(status().isNotFound());
        mvc.perform(get(path).servletPath(path)).andExpect(status().isOk()).andExpect(jsonPath("$.userId").doesNotExist());
        mvc.perform(as(other, get(path))).andExpect(status().isOk());
        mvc.perform(as(other, get(path + "/target"))).andExpect(status().isOk());
    }

    @Test
    void blocksDoNotPreventPersonalChatCreationOrMessaging() throws Exception {
        mvc.perform(as(actor, post(blockPath(target)))).andExpect(status().isOk());
        var response = mvc.perform(as(actor, post("/api/chat-rooms/personal"))
                        .contentType("application/json").content("{\"targetUserId\":" + target.getId() + "}"))
                .andExpect(status().isCreated()).andReturn();
        long roomId = new ObjectMapper().readTree(response.getResponse().getContentAsString()).path("chatRoomId").asLong();
        mvc.perform(as(actor, post("/api/chat-rooms/" + roomId + "/messages"))
                        .contentType("application/json").content("{\"content\":\"계속 가능한 채팅\"}"))
                .andExpect(status().isCreated());
        mvc.perform(as(target, get("/api/chat-rooms/" + roomId + "/messages")))
                .andExpect(status().isOk());
    }

    @Test
    void databaseRejectsDuplicateDirectionEvenIfServiceGuardWereBypassed() {
        blocks.saveAndFlush(UserBlock.create(actor, target));
        assertThatThrownBy(() -> blocks.saveAndFlush(UserBlock.create(actor, target)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void adminCanManageOwnBlocksWithoutSchoolVerificationButDoesNotBypassProfileBlocks() throws Exception {
        User admin = users.saveAndFlush(User.createAdmin("block-admin@example.com", "hash", "관리자"));
        mvc.perform(as(admin, post(blockPath(target)))).andExpect(status().isOk());
        mvc.perform(as(admin, get(detailPath(target)))).andExpect(status().isNotFound());
        mvc.perform(as(admin, delete(blockPath(target)))).andExpect(status().isOk());
        mvc.perform(as(admin, get(detailPath(target)))).andExpect(status().isOk());
    }

    @Test
    void validatesInputsAndKeepsLoginAndUniversityVerificationRequirements() throws Exception {
        mvc.perform(as(actor, post(blockPath(actor)))).andExpect(status().isBadRequest());
        mvc.perform(as(actor, post("/api/users/-1/block"))).andExpect(status().isBadRequest());
        mvc.perform(as(actor, post("/api/users/99999999/block"))).andExpect(status().isNotFound());
        mvc.perform(as(actor, get("/api/users/me/blocks?page=-1"))).andExpect(status().isBadRequest());
        mvc.perform(as(actor, get("/api/users/me/blocks?page=2147483647"))).andExpect(status().isBadRequest());
        User unverified = user("block-unverified", false);
        for (HttpMethod method : new HttpMethod[]{HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE}) {
            mvc.perform(request(method, blockPath(target)).servletPath(blockPath(target))).andExpect(status().isUnauthorized());
            mvc.perform(as(unverified, request(method, blockPath(target)))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/users/me/blocks").servletPath("/api/users/me/blocks")).andExpect(status().isUnauthorized());
        mvc.perform(as(unverified, get("/api/users/me/blocks"))).andExpect(status().isForbidden());
        assertThat(blocks.count()).isZero();
    }

    private User user(String name, boolean verified) {
        User user = User.createOAuthUser(name + "@example.com", name, User.OAuthProvider.KAKAO, name);
        if (verified) user.verifyUniversityEmail(name + "@uos.ac.kr");
        user = users.saveAndFlush(user);
        profiles.saveAndFlush(UserProfile.create(user, UserProfile.DepartmentType.COMPUTER_SCIENCE,
                UserProfile.ActivityStatus.LOOKING_FOR_TEAM, (short) 3, null, null, "소개", true, "[\"DEVELOPMENT\"]"));
        em.createNativeQuery("UPDATE user_profiles SET primary_roles = JSON '[\"DEVELOPMENT\"]' WHERE user_id = :id")
                .setParameter("id", user.getId()).executeUpdate();
        return user;
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwt.generateAccessToken(user)).with(servletRequest -> {
            servletRequest.setServletPath(servletRequest.getRequestURI());
            return servletRequest;
        });
    }

    private String blockPath(User user) { return "/api/users/" + user.getId() + "/block"; }
    private String detailPath(User user) { return "/api/profile/" + user.getId(); }
}
