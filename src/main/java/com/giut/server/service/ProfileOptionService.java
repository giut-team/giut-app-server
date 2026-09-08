package com.giut.server.service;

import com.giut.server.dto.profile.request.CreateSkillTagRequest;
import com.giut.server.dto.profile.response.CreateSkillTagResponse;
import com.giut.server.dto.profile.response.ProfileRoleListResponse;
import com.giut.server.dto.profile.response.ProfileRoleResponse;
import com.giut.server.dto.profile.response.ProfileTagListResponse;
import com.giut.server.dto.profile.response.ProfileTagResponse;
import com.giut.server.entity.ProfileRole;
import com.giut.server.entity.ProfileRoleSkillTag;
import com.giut.server.entity.ProfileTag;
import com.giut.server.exception.ResourceNotFoundException;
import com.giut.server.repository.ProfileRoleRepository;
import com.giut.server.repository.ProfileRoleSkillTagRepository;
import com.giut.server.repository.ProfileTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProfileOptionService {

    private final ProfileRoleRepository profileRoleRepository;

    private final ProfileRoleSkillTagRepository profileRoleSkillTagRepository;

    private final ProfileTagRepository profileTagRepository;

    @Transactional(readOnly = true)
    public ProfileRoleListResponse getRoles(String primaryRoleCode) {
        ProfileRole.PrimaryRole primaryRole = findPrimaryRole(primaryRoleCode);

        List<ProfileRoleResponse> roles = profileRoleRepository
                .findAllByPrimaryRoleOrderByDisplayOrderAsc(primaryRole).stream()
                .map(ProfileRoleResponse::from)
                .toList();

        if (roles.isEmpty()) {
            throw new ResourceNotFoundException("등록된 세부 역할이 없습니다.");
        }

        return new ProfileRoleListResponse(
                primaryRole.name(),
                primaryRole.getDisplayName(),
                roles
        );
    }

    private ProfileRole.PrimaryRole findPrimaryRole(String primaryRoleCode) {
        try {
            return ProfileRole.PrimaryRole.fromCode(primaryRoleCode);
        } catch (IllegalArgumentException exception) {
            throw new ResourceNotFoundException("존재하지 않는 대표 역할입니다.");
        }
    }

    @Transactional(readOnly = true)
    public ProfileTagListResponse getTags(ProfileTag.TagType type, List<String> relatedRoleCodes) {
        List<ProfileTag> profileTags = profileTagRepository.findAllByTagTypeOrderByIdAsc(type);

        if (type == ProfileTag.TagType.SKILL && !relatedRoleCodes.isEmpty()) {
            Set<Long> selectedRoleIds = findRoles(relatedRoleCodes).stream()
                    .map(ProfileRole::getId)
                    .collect(java.util.stream.Collectors.toSet());
            Set<Long> recommendedTagIds = profileRoleSkillTagRepository
                    .findAllByTag_IdIn(profileTags.stream().map(ProfileTag::getId).toList()).stream()
                    .filter(link -> selectedRoleIds.contains(link.getRole().getId()))
                    .map(link -> link.getTag().getId())
                    .collect(java.util.stream.Collectors.toSet());
            profileTags = profileTags.stream()
                    .filter(tag -> recommendedTagIds.contains(tag.getId()))
                    .toList();
        }

        List<ProfileTagResponse> tags = toTagResponses(profileTags);

        if (tags.isEmpty()) {
            throw new ResourceNotFoundException("등록된 프로필 태그가 없습니다.");
        }

        return new ProfileTagListResponse(type, tags);
    }

    @Transactional
    public CreateSkillTagResponse createSkill(CreateSkillTagRequest request) {
        String name = request.name().trim();
        String normalizedName = name.toLowerCase(Locale.ROOT);
        List<ProfileRole> relatedRoles = findRoles(request.relatedRoleCodes());

        ProfileTag skill = profileTagRepository
                .findByTagTypeAndNormalizedName(ProfileTag.TagType.SKILL, normalizedName)
                .orElse(null);
        boolean created = skill == null;

        if (created) {
            skill = profileTagRepository.save(ProfileTag.create(ProfileTag.TagType.SKILL, name, normalizedName));
        }

        linkSkillToRoles(skill, relatedRoles);
        ProfileTagResponse response = toTagResponses(List.of(skill)).getFirst();
        return new CreateSkillTagResponse(created, response);
    }

    private List<ProfileRole> findRoles(List<String> roleCodes) {
        Set<String> distinctCodes = new LinkedHashSet<>(roleCodes);
        if (distinctCodes.size() != roleCodes.size()) {
            throw new IllegalArgumentException("연결할 세부 역할은 중복해서 선택할 수 없습니다.");
        }

        List<ProfileRole> roles = profileRoleRepository.findAllByCodeIn(distinctCodes);
        if (roles.size() != distinctCodes.size()) {
            throw new IllegalArgumentException("지원하지 않는 세부 역할이 포함되어 있습니다.");
        }

        Map<String, ProfileRole> roleByCode = new HashMap<>();
        roles.forEach(role -> roleByCode.put(role.getCode(), role));
        return roleCodes.stream().map(roleByCode::get).toList();
    }

    private void linkSkillToRoles(ProfileTag skill, List<ProfileRole> roles) {
        Set<Long> linkedRoleIds = profileRoleSkillTagRepository.findAllByTag_IdIn(List.of(skill.getId())).stream()
                .map(link -> link.getRole().getId())
                .collect(java.util.stream.Collectors.toSet());

        profileRoleSkillTagRepository.saveAll(roles.stream()
                .filter(role -> !linkedRoleIds.contains(role.getId()))
                .map(role -> ProfileRoleSkillTag.create(role, skill))
                .toList());
    }

    private List<ProfileTagResponse> toTagResponses(List<ProfileTag> tags) {
        if (tags.isEmpty()) {
            return List.of();
        }

        Map<Long, List<ProfileRoleResponse>> relatedRolesByTagId = new HashMap<>();
        profileRoleSkillTagRepository.findAllByTag_IdIn(tags.stream().map(ProfileTag::getId).toList())
                .forEach(link -> relatedRolesByTagId
                        .computeIfAbsent(link.getTag().getId(), ignored -> new ArrayList<>())
                        .add(ProfileRoleResponse.from(link.getRole())));

        return tags.stream()
                .map(tag -> ProfileTagResponse.from(
                        tag,
                        relatedRolesByTagId.getOrDefault(tag.getId(), List.of())
                ))
                .toList();
    }
}
