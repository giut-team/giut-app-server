package com.giut.server.profile.service;

import com.giut.server.profile.dto.request.CreateSkillTagRequest;
import com.giut.server.profile.dto.response.CreateSkillTagResponse;
import com.giut.server.profile.entity.ProfileRole;
import com.giut.server.profile.entity.ProfileTag;
import com.giut.server.profile.repository.ProfileRoleRepository;
import com.giut.server.profile.repository.ProfileRoleSkillTagRepository;
import com.giut.server.profile.repository.ProfileTagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileOptionServiceTest {

    @Mock private ProfileRoleRepository profileRoleRepository;
    @Mock private ProfileRoleSkillTagRepository profileRoleSkillTagRepository;
    @Mock private ProfileTagRepository profileTagRepository;
    @InjectMocks private ProfileOptionService profileOptionService;

    @Test
    void newSkillTagLinksToRequestedRoles() {
        ProfileRole role = mock(ProfileRole.class);
        when(role.getId()).thenReturn(1L);
        when(role.getCode()).thenReturn("BACKEND_DEVELOPER");
        when(profileRoleRepository.findAllByCodeIn(any())).thenReturn(List.of(role));

        ProfileTag savedTag = mock(ProfileTag.class);
        when(savedTag.getId()).thenReturn(10L);
        when(profileTagRepository.findByTagTypeAndNormalizedName(ProfileTag.TagType.SKILL, "docker"))
                .thenReturn(Optional.empty());
        when(profileTagRepository.save(any(ProfileTag.class))).thenReturn(savedTag);
        when(profileRoleSkillTagRepository.findAllByTag_IdIn(List.of(10L))).thenReturn(List.of());

        CreateSkillTagResponse response = profileOptionService.createSkill(
                new CreateSkillTagRequest("Docker", List.of("BACKEND_DEVELOPER")));

        assertThat(response.created()).isTrue();
        verify(profileTagRepository).save(any(ProfileTag.class));
        verify(profileRoleSkillTagRepository).saveAll(any());
    }

    @Test
    void existingSkillTagIsNotRelinkedToNewRoles() {
        ProfileRole role = mock(ProfileRole.class);
        when(role.getCode()).thenReturn("UX_DESIGNER");
        when(profileRoleRepository.findAllByCodeIn(any())).thenReturn(List.of(role));

        ProfileTag existingTag = mock(ProfileTag.class);
        when(existingTag.getId()).thenReturn(5L);
        when(profileTagRepository.findByTagTypeAndNormalizedName(ProfileTag.TagType.SKILL, "java"))
                .thenReturn(Optional.of(existingTag));
        when(profileRoleSkillTagRepository.findAllByTag_IdIn(List.of(5L))).thenReturn(List.of());

        CreateSkillTagResponse response = profileOptionService.createSkill(
                new CreateSkillTagRequest("Java", List.of("UX_DESIGNER")));

        assertThat(response.created()).isFalse();
        verify(profileTagRepository, never()).save(any());
        verify(profileRoleSkillTagRepository, never()).saveAll(any());
    }
}
