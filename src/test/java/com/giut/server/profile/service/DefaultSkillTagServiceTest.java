package com.giut.server.profile.service;

import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.profile.entity.ProfileRole;
import com.giut.server.profile.entity.ProfileRoleSkillTag;
import com.giut.server.profile.entity.ProfileTag;
import com.giut.server.profile.repository.ProfileRoleRepository;
import com.giut.server.profile.repository.ProfileRoleSkillTagRepository;
import com.giut.server.profile.repository.ProfileTagRepository;
import com.giut.server.profile.seed.DefaultSkillTagCatalog;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultSkillTagServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ProfileRoleRepository roleRepository;
    @Mock private ProfileTagRepository tagRepository;
    @Mock private ProfileRoleSkillTagRepository linkRepository;

    private final DefaultSkillTagCatalog catalog = new DefaultSkillTagCatalog();
    private final Map<String, ProfileRole> roles = new HashMap<>();
    private final Map<String, ProfileTag> tags = new HashMap<>();
    private final List<ProfileRoleSkillTag> links = new ArrayList<>();
    private DefaultSkillTagService service;

    @BeforeEach
    void setUp() {
        AtomicLong roleId = new AtomicLong();
        catalog.skills().stream().flatMap(skill -> skill.relatedRoleCodes().stream()).distinct().forEach(code -> {
            ProfileRole role = BeanUtils.instantiateClass(ProfileRole.class);
            ReflectionTestUtils.setField(role, "id", roleId.incrementAndGet());
            ReflectionTestUtils.setField(role, "code", code);
            ReflectionTestUtils.setField(role, "name", code);
            roles.put(code, role);
        });
        service = new DefaultSkillTagService(
                userRepository, roleRepository,
                new ProfileOptionService(roleRepository, linkRepository, tagRepository), catalog
        );
    }

    @Test
    void realOptionServiceCreatesSkillsAndLinksOnceAndKeepsTheSameIdsOnRepeat() {
        stubStorage();

        var first = service.seedDefaults(10L);
        int firstLinkCount = links.size();
        var second = service.seedDefaults(10L);

        assertThat(first.createdCount()).isEqualTo(40);
        assertThat(first.existingCount()).isZero();
        assertThat(first.skillTagIds()).hasSize(40).doesNotHaveDuplicates();
        assertThat(second.createdCount()).isZero();
        assertThat(second.existingCount()).isEqualTo(40);
        assertThat(second.skillTagIds()).containsExactlyElementsOf(first.skillTagIds());
        assertThat(links).hasSize(firstLinkCount);
        assertThat(tags).hasSize(40);
        verify(tagRepository, times(40)).save(any(ProfileTag.class));
    }

    @Test
    void caseInsensitiveExistingSkillKeepsItsIdNameAndExtraLinksAndGetsMissingLinks() {
        ProfileTag docker = ProfileTag.create(ProfileTag.TagType.SKILL, "docker", "docker");
        ReflectionTestUtils.setField(docker, "id", 500L);
        tags.put("docker", docker);
        links.add(ProfileRoleSkillTag.create(roles.get("FRONTEND_DEVELOPER"), docker));
        stubStorage();

        var response = service.seedDefaults(10L);

        assertThat(response.createdCount()).isEqualTo(39);
        assertThat(response.existingCount()).isEqualTo(1);
        assertThat(response.skillTagIds().get(20)).isEqualTo(500L);
        assertThat(docker.getName()).isEqualTo("docker");
        assertThat(links.stream().filter(link -> link.getTag().getId().equals(500L)))
                .extracting(link -> link.getRole().getCode())
                .containsExactlyInAnyOrder("FRONTEND_DEVELOPER", "BACKEND_DEVELOPER", "DATA_ENGINEER", "AI_ENGINEER");
        verify(tagRepository, times(39)).save(any(ProfileTag.class));
    }

    @Test
    void missingRoleIsReportedBeforeAnySkillOrRoleLinkIsWritten() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(admin()));
        when(roleRepository.findAllByCodeIn(anyCollection())).thenReturn(
                roles.values().stream().filter(role -> !role.getCode().equals("BACKEND_DEVELOPER")).toList()
        );

        assertThatThrownBy(() -> service.seedDefaults(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/api/admin/profile/roles/defaults")
                .hasMessageContaining("BACKEND_DEVELOPER");
        verifyNoInteractions(tagRepository, linkRepository);
    }

    @Test
    void studentCannotRegisterDefaultSkills() {
        User user = admin();
        ReflectionTestUtils.setField(user, "role", User.Role.STUDENT);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        assertDenied();
    }

    @Test
    void suspendedAdminCannotRegisterDefaultSkills() {
        User user = admin();
        ReflectionTestUtils.setField(user, "status", User.Status.SUSPENDED);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        assertDenied();
    }

    @Test
    void missingAccountCannotRegisterDefaultSkills() {
        when(userRepository.findById(10L)).thenReturn(Optional.empty());
        assertDenied();
    }

    private void assertDenied() {
        assertThatThrownBy(() -> service.seedDefaults(10L)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(roleRepository, tagRepository, linkRepository);
    }

    private void stubStorage() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(admin()));
        when(roleRepository.findAllByCodeIn(anyCollection())).thenAnswer(invocation -> {
            Collection<String> codes = invocation.getArgument(0);
            return codes.stream().map(roles::get).filter(Objects::nonNull).toList();
        });
        when(tagRepository.findByTagTypeAndNormalizedName(eq(ProfileTag.TagType.SKILL), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(tags.get(invocation.getArgument(1))));
        AtomicLong nextId = new AtomicLong();
        when(tagRepository.save(any(ProfileTag.class))).thenAnswer(invocation -> {
            ProfileTag tag = invocation.getArgument(0);
            ReflectionTestUtils.setField(tag, "id", nextId.incrementAndGet());
            tags.put(tag.getNormalizedName(), tag);
            return tag;
        });
        when(linkRepository.findAllByTag_IdIn(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return links.stream().filter(link -> ids.contains(link.getTag().getId())).toList();
        });
        when(linkRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<ProfileRoleSkillTag> added = invocation.getArgument(0);
            List<ProfileRoleSkillTag> result = new ArrayList<>();
            for (ProfileRoleSkillTag link : added) {
                links.add(link);
                result.add(link);
            }
            return result;
        });
    }

    private User admin() {
        return User.createAdmin("admin@example.com", "password-hash", "관리자");
    }
}
