package com.giut.server.service;

import com.giut.server.dto.request.PutMyProfileRequest;
import com.giut.server.dto.response.*;
import com.giut.server.entity.ProfileLink;
import com.giut.server.entity.ProfileRole;
import com.giut.server.entity.ProfileRoleCategory;
import com.giut.server.entity.ProfileTag;
import com.giut.server.entity.UserProfile;
import com.giut.server.entity.UserProfilePrimaryRole;
import com.giut.server.entity.UserProfileRole;
import com.giut.server.entity.UserProfileTag;
import com.giut.server.repository.ProfileLinkRepository;
import com.giut.server.repository.ProfileRoleCategoryRepository;
import com.giut.server.repository.ProfileRoleRepository;
import com.giut.server.repository.ProfileTagRepository;
import com.giut.server.repository.UserProfileRoleRepository;
import com.giut.server.repository.UserProfilePrimaryRoleRepository;
import com.giut.server.repository.UserProfileRepository;
import com.giut.server.repository.UserProfileTagRepository;
import com.giut.server.dto.request.ProfileLinkRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    private final UserProfileRoleRepository userProfileRoleRepository;

    private final UserProfilePrimaryRoleRepository userProfilePrimaryRoleRepository;

    private final UserProfileTagRepository userProfileTagRepository;

    private final ProfileTagRepository profileTagRepository;

    private final ProfileLinkRepository profileLinkRepository;

    private final ProfileRoleCategoryRepository profileRoleCategoryRepository;

    private final ProfileRoleRepository profileRoleRepository;

    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(Long userId) {
        return userProfileRepository.findById(userId)
                .map(this::toMyProfileResponse)
                .orElseGet(MyProfileResponse::notCompleted);
    }

    @Transactional
    public MyProfileSaveResponse saveMyProfile(Long userId, PutMyProfileRequest request) {
        List<ProfileRoleCategory> primaryRoles = findPrimaryRoles(request.primaryRoles());
        List<ProfileRole> roles = findRoles(primaryRoles, request.roles());
        validateLinkTypes(request.links()); // 같은 링크로 등록하는게 있는지 체크

        UserProfile profile = userProfileRepository.findById(userId).orElse(null);
        boolean created = profile == null;

        if (created) {
            profile = userProfileRepository.save(UserProfile.create(
                    userId,
                    request.department(),
                    request.activityStatus(),
                    request.grade(),
                    request.gender(),
                    request.profileImageUrl(),
                    request.bio(),
                    request.searchable()
            ));
        } else {
            profile.update(
                    request.department(),
                    request.activityStatus(),
                    request.grade(),
                    request.gender(),
                    request.profileImageUrl(),
                    request.bio(),
                    request.searchable()
            );
        }

        replacePrimaryRoles(userId, primaryRoles);
        replaceRoles(userId, roles);
        replaceTags(userId, request);
        replaceLinks(userId, request);

        return new MyProfileSaveResponse(toMyProfileResponse(profile), created);
    }

    private List<ProfileRoleCategory> findPrimaryRoles(List<String> primaryRoleCodes) {
        Set<String> distinctCodes = new LinkedHashSet<>(primaryRoleCodes);
        if (distinctCodes.size() != primaryRoleCodes.size()) {
            throw new IllegalArgumentException("대표 역할은 중복해서 선택할 수 없습니다.");
        }

        List<ProfileRoleCategory> primaryRoles = profileRoleCategoryRepository.findAllByCodeIn(distinctCodes);
        if (primaryRoles.size() != distinctCodes.size()) {
            throw new IllegalArgumentException("지원하지 않는 대표 역할이 포함되어 있습니다.");
        }

        Map<String, ProfileRoleCategory> primaryRoleByCode = new HashMap<>();
        primaryRoles.forEach(primaryRole -> primaryRoleByCode.put(primaryRole.getCode(), primaryRole));
        return primaryRoleCodes.stream().map(primaryRoleByCode::get).toList();
    }

    private List<ProfileRole> findRoles(List<ProfileRoleCategory> primaryRoles, List<String> roleCodes) {
        if (new HashSet<>(roleCodes).size() != roleCodes.size()) {
            throw new IllegalArgumentException("세부 역할은 중복해서 선택할 수 없습니다.");
        }

        List<ProfileRole> roles = profileRoleRepository.findAllByCodeIn(roleCodes);
        if (roles.size() != roleCodes.size()) {
            throw new IllegalArgumentException("지원하지 않는 세부 역할이 포함되어 있습니다.");
        }

        Set<Long> selectedPrimaryRoleIds = primaryRoles.stream()
                .map(ProfileRoleCategory::getId)
                .collect(java.util.stream.Collectors.toSet());
        boolean hasUnselectedPrimaryRole = roles.stream()
                .anyMatch(role -> !selectedPrimaryRoleIds.contains(role.getPrimaryRole().getId()));

        if (hasUnselectedPrimaryRole) {
            throw new IllegalArgumentException("세부 역할은 선택한 대표 역할 분야에서만 선택할 수 있습니다.");
        }

        Map<String, ProfileRole> roleByCode = new HashMap<>();
        roles.forEach(role -> roleByCode.put(role.getCode(), role));
        return roleCodes.stream().map(roleByCode::get).toList();
    }

    private void validateLinkTypes(List<ProfileLinkRequest> links) {
        Set<ProfileLink.Type> linkTypes = new HashSet<>();
        for (ProfileLinkRequest link : links) {
            if (!linkTypes.add(link.type())) {
                throw new IllegalArgumentException("같은 유형의 외부 링크는 하나만 등록할 수 있습니다.");
            }
        }
    }

    private void replaceRoles(Long userId, List<ProfileRole> roles) {
        userProfileRoleRepository.deleteByUserId(userId);
        userProfileRoleRepository.flush();

        userProfileRoleRepository.saveAll(roles.stream()
                .map(role -> UserProfileRole.create(userId, role))
                .toList());
    }

    private void replacePrimaryRoles(Long userId, List<ProfileRoleCategory> primaryRoles) {
        userProfilePrimaryRoleRepository.deleteByUserId(userId);
        userProfilePrimaryRoleRepository.flush();

        userProfilePrimaryRoleRepository.saveAll(primaryRoles.stream()
                .map(primaryRole -> UserProfilePrimaryRole.create(userId, primaryRole))
                .toList());
    }

    private void replaceTags(Long userId, PutMyProfileRequest request) {
        List<ProfileTag> skills = new ArrayList<>(findTags(request.skillTagIds(), ProfileTag.TagType.SKILL));
        skills.addAll(resolveCustomSkills(request.customSkills()));
        List<ProfileTag> distinctSkills = distinctTags(skills);

        if (distinctSkills.size() > 3) {
            throw new IllegalArgumentException("기술 스택은 직접 입력 항목을 포함해 최대 3개까지 선택할 수 있습니다.");
        }

        List<ProfileTag> allTags = new ArrayList<>(distinctSkills);
        allTags.addAll(findTags(request.interestTagIds(), ProfileTag.TagType.INTEREST));
        allTags.addAll(findTags(request.experienceTagIds(), ProfileTag.TagType.EXPERIENCE));

        userProfileTagRepository.deleteByUserId(userId);
        userProfileTagRepository.flush();

        userProfileTagRepository.saveAll(allTags.stream()
                .map(tag -> UserProfileTag.create(userId, tag.getId()))
                .toList());
    }

    private List<ProfileTag> findTags(List<Long> tagIds, ProfileTag.TagType tagType) {
        if (tagIds.contains(null)) {
            throw new IllegalArgumentException("프로필 태그 ID에는 null을 넣을 수 없습니다.");
        }

        Set<Long> distinctIds = new LinkedHashSet<>(tagIds);
        if (distinctIds.size() != tagIds.size()) {
            throw new IllegalArgumentException("프로필 태그는 중복해서 선택할 수 없습니다.");
        }

        List<ProfileTag> tags = profileTagRepository.findAllById(distinctIds);
        if (tags.size() != distinctIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 프로필 태그가 포함되어 있습니다.");
        }

        if (tags.stream().anyMatch(tag -> tag.getTagType() != tagType)) {
            throw new IllegalArgumentException("태그 종류가 올바르지 않습니다.");
        }

        Map<Long, ProfileTag> tagById = new HashMap<>();
        tags.forEach(tag -> tagById.put(tag.getId(), tag));
        return tagIds.stream().map(tagById::get).toList();
    }

    private List<ProfileTag> resolveCustomSkills(List<String> customSkills) {
        Set<String> normalizedNames = new HashSet<>();
        List<ProfileTag> tags = new ArrayList<>();

        for (String customSkill : customSkills) {
            String name = customSkill.trim();
            String normalizedName = name.toLowerCase(Locale.ROOT);
            if (!normalizedNames.add(normalizedName)) {
                throw new IllegalArgumentException("직접 입력 기술 스택은 중복해서 입력할 수 없습니다.");
            }

            ProfileTag tag = profileTagRepository
                    .findByTagTypeAndNormalizedName(ProfileTag.TagType.SKILL, normalizedName)
                    .orElseGet(() -> profileTagRepository.save(
                            ProfileTag.create(ProfileTag.TagType.SKILL, name, normalizedName)
                    ));
            tags.add(tag);
        }

        return tags;
    }

    private List<ProfileTag> distinctTags(List<ProfileTag> tags) {
        Map<Long, ProfileTag> tagById = new LinkedHashMap<>();
        tags.forEach(tag -> tagById.putIfAbsent(tag.getId(), tag));
        return new ArrayList<>(tagById.values());
    }

    private void replaceLinks(Long userId, PutMyProfileRequest request) {
        profileLinkRepository.deleteByUserId(userId);
        profileLinkRepository.flush();

        profileLinkRepository.saveAll(request.links().stream()
                .map(link -> ProfileLink.create(userId, link.type(), link.url(), link.title()))
                .toList());
    }

    private MyProfileResponse toMyProfileResponse(UserProfile profile) {
        List<ProfilePrimaryRoleResponse> primaryRoles = userProfilePrimaryRoleRepository
                .findAllByUserId(profile.getUserId()).stream()
                .map(UserProfilePrimaryRole::getPrimaryRole)
                .sorted(java.util.Comparator.comparingInt(ProfileRoleCategory::getDisplayOrder))
                .map(ProfilePrimaryRoleResponse::from)
                .toList();

        List<ProfileRoleResponse> roles = userProfileRoleRepository.findAllByUserId(profile.getUserId()).stream()
                .map(UserProfileRole::getRole)
                .map(ProfileRoleResponse::from)
                .toList();

        List<Long> tagIds = userProfileTagRepository.findAllByUserId(profile.getUserId()).stream()
                .map(UserProfileTag::getTagId)
                .toList();
        Map<Long, ProfileTag> tagById = new HashMap<>();
        profileTagRepository.findAllById(tagIds).forEach(tag -> tagById.put(tag.getId(), tag));
        List<ProfileTagResponse> tags = tagIds.stream()
                .map(tagById::get)
                .filter(java.util.Objects::nonNull)
                .map(ProfileTagResponse::from)
                .toList();

        List<ProfileLinkResponse> links = profileLinkRepository
                .findAllByUserIdOrderByIdAsc(profile.getUserId()).stream()
                .map(ProfileLinkResponse::from)
                .toList();

        return MyProfileResponse.completed(ProfileResponse.from(profile, primaryRoles, roles, tags, links));
    }
}
