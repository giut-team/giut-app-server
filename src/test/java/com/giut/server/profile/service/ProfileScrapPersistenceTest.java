package com.giut.server.profile.service;

import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.repository.ProfileScrapRepository;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
class ProfileScrapPersistenceTest {
    @Autowired private UserRepository users;
    @Autowired private UserProfileRepository profiles;
    @Autowired private ProfileScrapRepository scraps;
    @Autowired private ProfileScrapService service;
    @Autowired private EntityManager em;
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;
    private User viewer;
    private User target;

    @BeforeEach
    void setUp() {
        viewer = users.saveAndFlush(User.createOAuthUser("scrap-viewer@example.com", "회원", User.OAuthProvider.KAKAO, "scrap-viewer"));
        target = users.saveAndFlush(User.createOAuthUser("scrap-target@example.com", "상대", User.OAuthProvider.KAKAO, "scrap-target"));
        profiles.saveAndFlush(UserProfile.create(target, UserProfile.DepartmentType.COMPUTER_SCIENCE,
                UserProfile.ActivityStatus.LOOKING_FOR_TEAM, (short) 3, null, null, "소개", true, "[]"));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void storesOnceRestoresAfterReloadAndRemovesOnlyCallersScrap() throws Exception {
        String path = "/api/profile/" + target.getId() + "/scrap";
        mvc.perform(post(path).with(user(viewer.getId().toString()).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scrapped").value(true));
        mvc.perform(post(path).with(user(viewer.getId().toString()).roles("ADMIN")))
                .andExpect(status().isOk());
        em.flush(); em.clear();
        assertThat(scraps.count()).isEqualTo(1);
        mvc.perform(get(path).with(user(viewer.getId().toString()).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scrapped").value(true));
        mvc.perform(get(path).with(user(target.getId().toString()).roles("ADMIN")))
                .andExpect(jsonPath("$.scrapped").value(false));
        mvc.perform(delete(path).with(user(target.getId().toString()).roles("ADMIN")))
                .andExpect(status().isOk());
        assertThat(service.getStatus(viewer.getId(), target.getId()).scrapped()).isTrue();
        mvc.perform(delete(path).with(user(viewer.getId().toString()).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scrapped").value(false));
        em.flush(); em.clear();
        assertThat(scraps.count()).isZero();
    }

    @Test
    void rejectsSelfAndHiddenProfileAndAnonymousWrites() throws Exception {
        String self = "/api/profile/" + viewer.getId() + "/scrap";
        mvc.perform(post(self).with(user(viewer.getId().toString()).roles("ADMIN")))
                .andExpect(status().isBadRequest());
        org.springframework.test.util.ReflectionTestUtils.setField(profiles.findById(target.getId()).orElseThrow(), "searchable", false);
        mvc.perform(post("/api/profile/" + target.getId() + "/scrap")
                .with(user(viewer.getId().toString()).roles("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(post(self)).andExpect(status().isUnauthorized());
        assertThat(scraps.count()).isZero();
    }
}
