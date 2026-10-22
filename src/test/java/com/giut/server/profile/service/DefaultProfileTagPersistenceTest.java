package com.giut.server.profile.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.profile.entity.ProfileRole;
import com.giut.server.profile.entity.ProfileTag;
import com.giut.server.profile.repository.ProfileRoleRepository;
import com.giut.server.profile.repository.ProfileRoleSkillTagRepository;
import com.giut.server.profile.repository.ProfileTagRepository;
import com.giut.server.profile.seed.DefaultSkillTagCatalog;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_context;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@Transactional
class DefaultProfileTagPersistenceTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRoleRepository roleRepository;
    @Autowired private ProfileTagRepository tagRepository;
    @Autowired private ProfileRoleSkillTagRepository linkRepository;
    @Autowired private DefaultSkillTagCatalog skillCatalog;
    @Autowired private DefaultSkillTagService skillService;
    @Autowired private DefaultProfileTagService service;
    @Autowired private EntityManager entityManager;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private WebApplicationContext context;

    private User admin;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        admin = userRepository.saveAndFlush(User.createAdmin("tag-seed-admin@example.com", "hash", "관리자"));
        var roles = skillCatalog.skills().stream().flatMap(skill -> skill.relatedRoleCodes().stream())
                .distinct().map(code -> {
                    ProfileRole role = BeanUtils.instantiateClass(ProfileRole.class);
                    ReflectionTestUtils.setField(role, "code", code);
                    ReflectionTestUtils.setField(role, "name", code);
                    ReflectionTestUtils.setField(role, "primaryRole", ProfileRole.PrimaryRole.DEVELOPMENT);
                    ReflectionTestUtils.setField(role, "displayOrder", 1);
                    return role;
                }).toList();
        roleRepository.saveAllAndFlush(roles);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @Timeout(30)
    void fillsMissingTypesAlongsideExistingSkillsAndAllThreeReadApisRespond() throws Exception {
        var originalSkills = skillService.seedDefaults(admin.getId());
        String token = jwtProvider.generateAccessToken(admin);
        for (String type : new String[]{"INTEREST", "EXPERIENCE"}) {
            mockMvc.perform(get("/api/profile/tags").param("type", type)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
        }

        var first = service.seedDefaults(admin.getId());
        entityManager.flush();
        entityManager.clear();
        long linkCount = linkRepository.count();
        var repeated = service.seedDefaults(admin.getId());

        assertThat(first.createdCount()).isZero();
        assertThat(first.existingCount()).isEqualTo(40);
        assertThat(first.skillTagIds()).containsExactlyElementsOf(originalSkills.skillTagIds());
        assertThat(first.interestTags().createdCount()).isEqualTo(5);
        assertThat(first.experienceTags().createdCount()).isEqualTo(8);
        assertThat(repeated.interestTags().createdCount()).isZero();
        assertThat(repeated.interestTags().existingCount()).isEqualTo(5);
        assertThat(repeated.experienceTags().createdCount()).isZero();
        assertThat(repeated.experienceTags().existingCount()).isEqualTo(8);
        assertThat(repeated.interestTags().tagIds()).containsExactlyElementsOf(first.interestTags().tagIds());
        assertThat(repeated.experienceTags().tagIds()).containsExactlyElementsOf(first.experienceTags().tagIds());
        assertThat(tagRepository.count()).isEqualTo(53);
        assertThat(linkRepository.count()).isEqualTo(linkCount);

        assertReadWorks(token, "SKILL", 40);
        assertReadWorks(token, "INTEREST", 5);
        assertReadWorks(token, "EXPERIENCE", 8);

        var interest = tagRepository.findByTagTypeAndNormalizedName(ProfileTag.TagType.INTEREST, "창업").orElseThrow();
        var experience = tagRepository.findByTagTypeAndNormalizedName(ProfileTag.TagType.EXPERIENCE, "창업").orElseThrow();
        assertThat(interest.getId()).isNotEqualTo(experience.getId());
    }

    @Test
    void keepsExistingNamesIdsAndCustomTags() {
        ProfileTag existing = tagRepository.saveAndFlush(ProfileTag.create(ProfileTag.TagType.INTEREST, "it/과학", "it/과학"));
        ProfileTag custom = tagRepository.saveAndFlush(ProfileTag.create(ProfileTag.TagType.EXPERIENCE, "개인활동", "개인활동"));

        var response = service.seedDefaults(admin.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(response.interestTags().createdCount()).isEqualTo(4);
        assertThat(response.interestTags().existingCount()).isEqualTo(1);
        assertThat(response.interestTags().tagIds().getFirst()).isEqualTo(existing.getId());
        assertThat(tagRepository.findById(existing.getId()).orElseThrow().getName()).isEqualTo("it/과학");
        assertThat(tagRepository.findById(custom.getId()).orElseThrow().getName()).isEqualTo("개인활동");
        assertThat(tagRepository.count()).isEqualTo(54);
    }

    @Test
    void missingRolesPreventEveryTagTypeFromBeingWritten() {
        roleRepository.deleteAllInBatch();
        entityManager.clear();

        assertThatThrownBy(() -> service.seedDefaults(admin.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("기본 역할을 먼저 등록");
        assertThat(tagRepository.count()).isZero();
    }

    private void assertReadWorks(String token, String type, int expectedCount) throws Exception {
        mockMvc.perform(get("/api/profile/tags").param("type", type)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value(type))
                .andExpect(jsonPath("$.tags.length()").value(expectedCount));
    }
}
