package com.giut.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giut.server.dto.request.PutMyProfileRequest;
import com.giut.server.dto.response.*;
import com.giut.server.entity.ProfileLink;
import com.giut.server.entity.ProfileRole;
import com.giut.server.entity.ProfileRoleSkillTag;
import com.giut.server.entity.ProfileTag;
import com.giut.server.entity.User;
import com.giut.server.entity.UserProfile;
import com.giut.server.entity.UserProfileRole;
import com.giut.server.entity.UserProfileTag;
import com.giut.server.repository.ProfileRoleRepository;
import com.giut.server.repository.ProfileRoleSkillTagRepository;
import com.giut.server.repository.ProfileTagRepository;
import com.giut.server.repository.UserProfileRoleRepository;
import com.giut.server.repository.UserProfileRepository;
import com.giut.server.repository.UserProfileTagRepository;
import com.giut.server.repository.UserRepository;
import com.giut.server.dto.request.ProfileLinkRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    private final UserRepository userRepository;

    private final UserProfileRoleRepository userProfileRoleRepository;

    private final UserProfileTagRepository userProfileTagRepository;

    private final ProfileTagRepository profileTagRepository;

    private final ProfileRoleRepository profileRoleRepository;

    private final ProfileRoleSkillTagRepository profileRoleSkillTagRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(Long userId) {
        return userProfileRepository.findById(userId)
                .map(this::toMyProfileResponse)
                .orElseGet(MyProfileResponse::notCompleted);
    }

    /**
     * 기웃허브 목록에 노출할 공개 프로필을 일괄 조회한다.
     * 프로필 수와 관계없이 프로필, 사용자, 역할, 사용자 태그, 태그를 각각 한 번씩 조회한다.
     */
    @Transactional(readOnly = true)
    public PublicProfileListResponse getPublicProfiles() {
        List<UserProfile> profiles = userProfileRepository
                .findAllBySearchableTrueAndActivityStatusNot(UserProfile.ActivityStatus.RESTING);
        if (profiles.isEmpty()) {
            return new PublicProfileListResponse(List.of());
        }

        List<Long> profileUserIds = profiles.stream().map(UserProfile::getUserId).toList();
        Map<Long, User> userById = new HashMap<>();
        userRepository.findAllByIdInAndStatus(profileUserIds, User.Status.ACTIVE)
                .forEach(user -> userById.put(user.getId(), user));

        List<Long> publicUserIds = profiles.stream()
                .map(UserProfile::getUserId)
                .filter(userById::containsKey)
                .toList();
        if (publicUserIds.isEmpty()) {
            return new PublicProfileListResponse(List.of());
        }

        Map<Long, List<ProfileRoleResponse>> rolesByUserId = findRolesByUserId(publicUserIds);
        Map<Long, List<ProfileTagSummaryResponse>> tagsByUserId = findTagsByUserId(publicUserIds);

        List<PublicProfileResponse> publicProfiles = profiles.stream()
                .filter(profile -> userById.containsKey(profile.getUserId()))
                .map(profile -> toPublicProfileResponse(
                        profile,
                        userById.get(profile.getUserId()),
                        rolesByUserId.getOrDefault(profile.getUserId(), List.of()),
                        tagsByUserId.getOrDefault(profile.getUserId(), List.of())
                ))
                .toList();

        return new PublicProfileListResponse(publicProfiles);
    }

    @Transactional
    public MyProfileSaveResponse saveMyProfile(Long userId, PutMyProfileRequest request) {
        List<ProfileRole.PrimaryRole> primaryRoles = findPrimaryRoles(request.primaryRoles());
        List<ProfileRole> roles = findRoles(primaryRoles, request.roles());
        validateLinkTypes(request.links()); // 같은 링크로 등록하는게 있는지 체크
        String primaryRolesJson = writeJson(primaryRoles.stream().map(Enum::name).toList());
        String externalLinksJson = writeJson(request.links().stream()
                .map(link -> new ProfileLink(link.type(), link.url(), link.title()))
                .toList());

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
                    request.searchable(),
                    primaryRolesJson,
                    externalLinksJson
            ));
        } else {
            profile.update(
                    request.department(),
                    request.activityStatus(),
                    request.grade(),
                    request.gender(),
                    request.profileImageUrl(),
                    request.bio(),
                    request.searchable(),
                    primaryRolesJson,
                    externalLinksJson
            );
        }

        replaceRoles(userId, roles);
        replaceTags(userId, request);

        return new MyProfileSaveResponse(toMyProfileResponse(profile), created);
    }

    private List<ProfileRole.PrimaryRole> findPrimaryRoles(List<String> primaryRoleCodes) {
        Set<String> distinctCodes = new LinkedHashSet<>(primaryRoleCodes);
        if (distinctCodes.size() != primaryRoleCodes.size()) {
            throw new IllegalArgumentException("대표 역할은 중복해서 선택할 수 없습니다.");
        }

        return primaryRoleCodes.stream().map(ProfileRole.PrimaryRole::fromCode).toList();
    }

    private List<ProfileRole> findRoles(List<ProfileRole.PrimaryRole> primaryRoles, List<String> roleCodes) {
        if (new HashSet<>(roleCodes).size() != roleCodes.size()) {
            throw new IllegalArgumentException("세부 역할은 중복해서 선택할 수 없습니다.");
        }

        List<ProfileRole> roles = profileRoleRepository.findAllByCodeIn(roleCodes);
        if (roles.size() != roleCodes.size()) {
            throw new IllegalArgumentException("지원하지 않는 세부 역할이 포함되어 있습니다.");
        }

        boolean hasUnselectedPrimaryRole = roles.stream()
                .anyMatch(role -> !primaryRoles.contains(role.getPrimaryRole()));

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

    private void replaceTags(Long userId, PutMyProfileRequest request) {
        List<ProfileTag> allTags = new ArrayList<>(findTags(request.skillTagIds(), ProfileTag.TagType.SKILL));
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

    private MyProfileResponse toMyProfileResponse(UserProfile profile) {
        List<ProfilePrimaryRoleResponse> primaryRoles = findPrimaryRoles(readPrimaryRoleCodes(profile))
                .stream()
                .sorted(java.util.Comparator.comparingInt(ProfileRole.PrimaryRole::getDisplayOrder))
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
        Map<Long, List<ProfileRoleResponse>> relatedRolesByTagId = new HashMap<>();
        profileRoleSkillTagRepository.findAllByTag_IdIn(tagIds).forEach(link -> relatedRolesByTagId
                .computeIfAbsent(link.getTag().getId(), ignored -> new ArrayList<>())
                .add(ProfileRoleResponse.from(link.getRole())));
        List<ProfileTagResponse> tags = tagIds.stream()
                .map(tagById::get)
                .filter(java.util.Objects::nonNull)
                .map(tag -> ProfileTagResponse.from(
                        tag,
                        relatedRolesByTagId.getOrDefault(tag.getId(), List.of())
                ))
                .toList();

        List<ProfileLinkResponse> links = readLinks(profile)
                .stream()
                .map(ProfileLinkResponse::from)
                .toList();

        return MyProfileResponse.completed(ProfileResponse.from(profile, primaryRoles, roles, tags, links));
    }

    private Map<Long, List<ProfileRoleResponse>> findRolesByUserId(List<Long> userIds) {
        Map<Long, List<ProfileRoleResponse>> rolesByUserId = new HashMap<>();
        userProfileRoleRepository.findAllByUserIdIn(userIds).forEach(userProfileRole -> rolesByUserId
                .computeIfAbsent(userProfileRole.getUserId(), ignored -> new ArrayList<>())
                .add(ProfileRoleResponse.from(userProfileRole.getRole())));
        return rolesByUserId;
    }

    private Map<Long, List<ProfileTagSummaryResponse>> findTagsByUserId(List<Long> userIds) {
        List<UserProfileTag> userProfileTags = userProfileTagRepository.findAllByUserIdIn(userIds);
        Map<Long, ProfileTag> tagById = new HashMap<>();
        profileTagRepository.findAllById(userProfileTags.stream().map(UserProfileTag::getTagId).toList())
                .forEach(tag -> tagById.put(tag.getId(), tag));

        Map<Long, List<ProfileTagSummaryResponse>> tagsByUserId = new HashMap<>();
        userProfileTags.forEach(userProfileTag -> {
            ProfileTag tag = tagById.get(userProfileTag.getTagId());
            if (tag != null) {
                tagsByUserId
                        .computeIfAbsent(userProfileTag.getUserId(), ignored -> new ArrayList<>())
                        .add(ProfileTagSummaryResponse.from(tag));
            }
        });
        return tagsByUserId;
    }

    private PublicProfileResponse toPublicProfileResponse(
            UserProfile profile,
            User user,
            List<ProfileRoleResponse> roles,
            List<ProfileTagSummaryResponse> tags
    ) {
        List<ProfilePrimaryRoleResponse> primaryRoles = findPrimaryRoles(readPrimaryRoleCodes(profile)).stream()
                .sorted(java.util.Comparator.comparingInt(ProfileRole.PrimaryRole::getDisplayOrder))
                .map(ProfilePrimaryRoleResponse::from)
                .toList();

        return new PublicProfileResponse(
                profile.getUserId(),
                user.getNickname(),
                user.getUniversityVerifiedAt() != null,
                profile.getDepartment(),
                profile.getDepartment().getDisplayName(),
                profile.getGrade(),
                profile.getProfileImageUrl(),
                profile.getActivityStatus(),
                profile.getActivityStatus().getDisplayName(),
                profile.getBio(),
                primaryRoles,
                roles,
                tags
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("프로필 JSON 데이터를 저장할 수 없습니다.", exception);
        }
    }

    private List<String> readPrimaryRoleCodes(UserProfile profile) {
        return readJson(profile.getPrimaryRolesJson(), new TypeReference<>() {});
    }

    private List<ProfileLink> readLinks(UserProfile profile) {
        return readJson(profile.getExternalLinksJson(), new TypeReference<>() {});
    }

    private <T> T readJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 프로필 JSON 데이터를 읽을 수 없습니다.", exception);
        }
    }
}
