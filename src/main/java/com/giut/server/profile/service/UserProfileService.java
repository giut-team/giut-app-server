package com.giut.server.profile.service;

import com.giut.server.profile.dto.response.MyProfileResponse;
import com.giut.server.profile.dto.response.MyProfileSummaryResponse;
import com.giut.server.profile.repository.ProfileRecommendationRepository;
import com.giut.server.profile.repository.ProfileCollaborationRepository;
import com.giut.server.profile.dto.response.PortfolioItemListResponse;
import com.giut.server.profile.dto.response.ProfileResponse;
import com.giut.server.profile.dto.response.ProfileTagResponse;
import com.giut.server.profile.dto.response.ProfileTagSummaryResponse;
import com.giut.server.profile.dto.response.PublicProfileDetailResponse;
import com.giut.server.profile.dto.response.PublicProfileListResponse;
import com.giut.server.profile.dto.response.PublicProfileResponse;
import com.giut.server.profile.dto.response.SharedProfileResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giut.server.profile.dto.request.PutMyProfileRequest;
import com.giut.server.profile.dto.request.UpdateMyProfileRequest;
import com.giut.server.profile.dto.request.PublicProfileSearchRequest;
import com.giut.server.profile.dto.common.ActivityHistoryDto;
import com.giut.server.profile.dto.common.PortfolioItemDto;
import com.giut.server.profile.dto.common.ProfileCodeNameResponse;
import com.giut.server.profile.entity.ProfileRole;
import com.giut.server.profile.entity.ProfileTag;
import com.giut.server.profile.entity.PortfolioItem;
import com.giut.server.profile.entity.PortfolioItemRole;
import com.giut.server.team.entity.TeamMember;
import com.giut.server.user.entity.User;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.entity.UserProfileRole;
import com.giut.server.profile.entity.UserProfileTag;
import com.giut.server.global.exception.ConflictException;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.profile.repository.ProfileRoleRepository;
import com.giut.server.profile.repository.ActivityHistoryRepository;
import com.giut.server.competition.repository.CompetitionScrapRepository;
import com.giut.server.profile.repository.ProfileRoleSkillTagRepository;
import com.giut.server.profile.repository.ProfileTagRepository;
import com.giut.server.profile.repository.PortfolioItemRepository;
import com.giut.server.profile.repository.PortfolioItemRoleRepository;
import com.giut.server.profile.repository.PortfolioItemSkillTagRepository;
import com.giut.server.team.repository.TeamMemberRepository;
import com.giut.server.team.repository.TeamScrapRepository;
import com.giut.server.profile.repository.UserProfileRoleRepository;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.profile.repository.UserProfileTagRepository;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    private static final int PUBLIC_PROFILE_PAGE_SIZE = 5;

    private final UserProfileRepository userProfileRepository;

    private final UserRepository userRepository;

    private final UserProfileRoleRepository userProfileRoleRepository;

    private final UserProfileTagRepository userProfileTagRepository;

    private final ProfileTagRepository profileTagRepository;

    private final ProfileRoleRepository profileRoleRepository;

    private final ProfileRoleSkillTagRepository profileRoleSkillTagRepository;

    private final PortfolioItemRepository portfolioItemRepository;

    private final PortfolioItemSkillTagRepository portfolioItemSkillTagRepository;

    private final PortfolioItemRoleRepository portfolioItemRoleRepository;

    private final TeamMemberRepository teamMemberRepository;

    private final CompetitionScrapRepository competitionScrapRepository;

    private final TeamScrapRepository teamScrapRepository;

    private final ProfileRecommendationRepository profileRecommendationRepository;

    private final ProfileCollaborationRepository profileCollaborationRepository;

    private final ActivityHistoryRepository activityHistoryRepository;

    private final ActivityHistoryService activityHistoryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(Long userId) {
        return userProfileRepository.findById(userId)
                .map(this::toMyProfileResponse)
                .orElseGet(() -> MyProfileResponse.notCompleted(
                        isUniversityVerified(userId),
                        toMyProfileSummary(userId)
                ));
    }

    /**
     * 기웃허브 목록에 노출할 공개 프로필을 일괄 조회한다.
     * 프로필 수와 관계없이 프로필, 사용자, 역할, 사용자 태그, 태그를 각각 한 번씩 조회한다.
     */
    @Transactional(readOnly = true)
    public PublicProfileListResponse getPublicProfiles(Long currentUserId, PublicProfileSearchRequest request) {
        Page<UserProfile> profilePage = userProfileRepository.findPublicProfiles(
                UserProfile.ActivityStatus.RESTING.name(),
                User.Status.ACTIVE.name(),
                currentUserId,
                request.primaryRole() == null ? null : request.primaryRole().name(),
                request.normalizedRole(),
                request.activityStatus() == null ? null : request.activityStatus().name(),
                request.departmentType() == null ? null : request.departmentType().name(),
                request.grade(),
                request.skillTagId(),
                PageRequest.of(request.pageOrDefault(), PUBLIC_PROFILE_PAGE_SIZE)
        );
        List<UserProfile> profiles = profilePage.getContent();
        if (profiles.isEmpty()) {
            return toPublicProfileListResponse(profilePage, List.of());
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
            return toPublicProfileListResponse(profilePage, List.of());
        }

        Map<Long, List<ProfileTagSummaryResponse>> skillsByUserId = findSkillsByUserId(publicUserIds);

        List<PublicProfileResponse> publicProfiles = profiles.stream()
                .filter(profile -> userById.containsKey(profile.getUserId()))
                .map(profile -> toPublicProfileResponse(
                        profile,
                        userById.get(profile.getUserId()),
                        skillsByUserId.getOrDefault(profile.getUserId(), List.of())
                ))
                .toList();

        return toPublicProfileListResponse(profilePage, publicProfiles);
    }

    @Transactional(readOnly = true)
    public PublicProfileDetailResponse getPublicProfile(Long userId) {
        UserProfile profile = findPublicProfile(userId);
        User user = findActiveUser(userId);

        return new PublicProfileDetailResponse(
                user.getNickname(),
                user.getUniversityVerifiedAt() != null,
                toProfileResponse(profile, false)
        );
    }

    @Transactional(readOnly = true)
    public SharedProfileResponse getSharedProfile(Long userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("공유 프로필을 찾을 수 없습니다."));
        User user = userRepository.findById(userId)
                .filter(found -> found.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("공유 프로필을 찾을 수 없습니다."));

        List<ProfileTagSummaryResponse> skills = findSkillsByUserId(List.of(userId))
                .getOrDefault(userId, List.of());
        return SharedProfileResponse.from(toPublicProfileResponse(profile, user, skills));
    }

    @Transactional(readOnly = true)
    public PortfolioItemListResponse getPublicPortfolioItems(Long userId) {
        findPublicProfile(userId);
        findActiveUser(userId);

        List<PortfolioItem> portfolioItems = portfolioItemRepository
                .findAllByUser_IdAndShowcaseOrderIsNotNullOrderByShowcaseOrderAsc(userId);
        Map<Long, List<ProfileTagSummaryResponse>> skillTagsByPortfolioItemId =
                findPortfolioSkillTagsByItemId(portfolioItems);
        Map<Long, List<ProfileCodeNameResponse>> rolesByPortfolioItemId =
                findPortfolioRolesByItemId(portfolioItems);

        return new PortfolioItemListResponse(portfolioItems.stream()
                .map(portfolioItem -> PortfolioItemDto.from(
                        portfolioItem,
                        skillTagsByPortfolioItemId.getOrDefault(portfolioItem.getId(), List.of()),
                        rolesByPortfolioItemId.getOrDefault(portfolioItem.getId(), List.of())
                ))
                .toList());
    }

    @Transactional
    public MyProfileResponse createMyProfile(Long userId, PutMyProfileRequest request) {
        return saveMyProfile(userId, request);
    }

    @Transactional
    public MyProfileResponse updateMyProfile(Long userId, UpdateMyProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("수정할 프로필을 찾을 수 없습니다."));
        List<ProfileRole.PrimaryRole> primaryRoles = findPrimaryRoles(request.primaryRoles());
        List<ProfileRole> roles = findRoles(primaryRoles, request.roles());
        String primaryRolesJson = writeJson(primaryRoles.stream().map(Enum::name).toList());

        profile.update(
                request.department(),
                request.activityStatus(),
                request.grade(),
                profile.getGender(),
                request.profileImageUrl(),
                request.bio(),
                request.searchable(),
                primaryRolesJson
        );
        replaceRoles(profile, roles);
        replaceTags(profile, request.skillTagIds(), request.interestTagIds(), request.experienceTagIds());
        activityHistoryService.replaceForProfile(user, request.activityHistories());

        return toMyProfileResponse(profile);
    }

    private MyProfileResponse saveMyProfile(Long userId, PutMyProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        List<ProfileRole.PrimaryRole> primaryRoles = findPrimaryRoles(request.primaryRoles());
        List<ProfileRole> roles = findRoles(primaryRoles, request.roles());
        String primaryRolesJson = writeJson(primaryRoles.stream().map(Enum::name).toList());

        if (userProfileRepository.findById(userId).isPresent()) {
            throw new ConflictException("이미 프로필이 등록되어 있습니다.");
        }

        UserProfile profile = userProfileRepository.save(UserProfile.create(
                user,
                request.department(),
                request.activityStatus(),
                request.grade(),
                null,
                request.profileImageUrl(),
                request.bio(),
                request.searchable(),
                primaryRolesJson
        ));

        replaceRoles(profile, roles);
        replaceTags(profile, request.skillTagIds(), request.interestTagIds(), request.experienceTagIds());
        activityHistoryService.createForProfile(user, request.activityHistories());

        return toMyProfileResponse(profile);
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

    private void replaceRoles(UserProfile profile, List<ProfileRole> roles) {
        userProfileRoleRepository.deleteByProfile_UserId(profile.getUserId());
        userProfileRoleRepository.flush();

        userProfileRoleRepository.saveAll(roles.stream()
                .map(role -> UserProfileRole.create(profile, role))
                .toList());
    }

    private void replaceTags(
            UserProfile profile,
            List<Long> skillTagIds,
            List<Long> interestTagIds,
            List<Long> experienceTagIds
    ) {
        List<ProfileTag> allTags = new ArrayList<>(findTags(skillTagIds, ProfileTag.TagType.SKILL));
        allTags.addAll(findTags(interestTagIds, ProfileTag.TagType.INTEREST));
        allTags.addAll(findTags(experienceTagIds, ProfileTag.TagType.EXPERIENCE));

        userProfileTagRepository.deleteByProfile_UserId(profile.getUserId());
        userProfileTagRepository.flush();

        userProfileTagRepository.saveAll(allTags.stream()
                .map(tag -> UserProfileTag.create(profile, tag))
                .toList());
    }

    private List<ProfileTag> findTags(List<Long> tagIds, ProfileTag.TagType tagType) {
        if (tagIds.stream().anyMatch(java.util.Objects::isNull)) {
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
        return MyProfileResponse.completed(
                profile.getUser().getUniversityVerifiedAt() != null,
                toProfileResponse(profile, true),
                toMyProfileSummary(profile.getUserId())
        );
    }

    private boolean isUniversityVerified(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getUniversityVerifiedAt() != null)
                .orElse(false);
    }

    private MyProfileSummaryResponse toMyProfileSummary(Long userId) {
        long competitionScrapCount = competitionScrapRepository.countByUser_Id(userId);
        long teamScrapCount = teamScrapRepository.countByUser_Id(userId);
        return new MyProfileSummaryResponse(
                portfolioItemRepository.countByUser_Id(userId),
                portfolioItemRepository.countByUser_IdAndShowcaseOrderIsNotNull(userId),
                teamMemberRepository.countByUserIdAndStatus(userId, TeamMember.Status.ACTIVE),
                competitionScrapCount + teamScrapCount,
                competitionScrapCount,
                teamScrapCount,
                profileRecommendationRepository.countByRecommendedUser_Id(userId),
                profileCollaborationRepository.countParticipatedTeams(userId)
        );
    }

    private UserProfile findPublicProfile(Long userId) {
        return userProfileRepository.findById(userId)
                .filter(UserProfile::isSearchable)
                .filter(profile -> profile.getActivityStatus() != UserProfile.ActivityStatus.RESTING)
                .orElseThrow(() -> new ResourceNotFoundException("공개 프로필을 찾을 수 없습니다."));
    }

    private User findActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("공개 프로필을 찾을 수 없습니다."));
    }

    private ProfileResponse toProfileResponse(UserProfile profile, boolean includeHiddenPortfolioItems) {
        List<ProfileCodeNameResponse> primaryRoles = findPrimaryRoles(readPrimaryRoleCodes(profile))
                .stream()
                .sorted(java.util.Comparator.comparingInt(ProfileRole.PrimaryRole::getDisplayOrder))
                .map(ProfileCodeNameResponse::from)
                .toList();

        List<ProfileCodeNameResponse> roles = userProfileRoleRepository.findAllByProfile_UserId(profile.getUserId()).stream()
                .map(UserProfileRole::getRole)
                .map(ProfileCodeNameResponse::from)
                .toList();

        List<Long> tagIds = userProfileTagRepository.findAllByProfile_UserId(profile.getUserId()).stream()
                .map(userProfileTag -> userProfileTag.getTag().getId())
                .toList();
        Map<Long, ProfileTag> tagById = new HashMap<>();
        profileTagRepository.findAllById(tagIds).forEach(tag -> tagById.put(tag.getId(), tag));
        Map<Long, List<ProfileCodeNameResponse>> relatedRolesByTagId = new HashMap<>();
        profileRoleSkillTagRepository.findAllByTag_IdIn(tagIds).forEach(link -> relatedRolesByTagId
                .computeIfAbsent(link.getTag().getId(), ignored -> new ArrayList<>())
                .add(ProfileCodeNameResponse.from(link.getRole())));
        List<ProfileTagResponse> tags = tagIds.stream()
                .map(tagById::get)
                .filter(java.util.Objects::nonNull)
                .map(tag -> ProfileTagResponse.from(
                        tag,
                        relatedRolesByTagId.getOrDefault(tag.getId(), List.of())
                ))
                .toList();

        List<PortfolioItem> portfolioItemEntities = includeHiddenPortfolioItems
                ? portfolioItemRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(profile.getUserId())
                : portfolioItemRepository.findAllByUser_IdAndShowcaseOrderIsNotNullOrderByShowcaseOrderAsc(profile.getUserId());
        Map<Long, List<ProfileTagSummaryResponse>> portfolioSkillTagsByItemId = findPortfolioSkillTagsByItemId(portfolioItemEntities);
        Map<Long, List<ProfileCodeNameResponse>> portfolioRolesByItemId = findPortfolioRolesByItemId(portfolioItemEntities);
        List<PortfolioItemDto> portfolioItems = portfolioItemEntities
                .stream()
                .map(portfolioItem -> PortfolioItemDto.from(
                        portfolioItem,
                        portfolioSkillTagsByItemId.getOrDefault(portfolioItem.getId(), List.of()),
                        portfolioRolesByItemId.getOrDefault(portfolioItem.getId(), List.of())
                ))
                .toList();

        List<ActivityHistoryDto> activityHistories = activityHistoryRepository
                .findAllByUser_IdOrderByStartMonthDescEndMonthDescIdDesc(profile.getUserId())
                .stream()
                .map(ActivityHistoryDto::from)
                .toList();

        return ProfileResponse.from(profile, primaryRoles, roles, tags, portfolioItems, activityHistories);
    }

    private Map<Long, List<ProfileTagSummaryResponse>> findSkillsByUserId(List<Long> userIds) {
        List<UserProfileTag> userProfileTags = userProfileTagRepository.findAllByProfile_UserIdIn(userIds);
        Map<Long, ProfileTag> tagById = new HashMap<>();
        profileTagRepository.findAllById(userProfileTags.stream().map(userProfileTag -> userProfileTag.getTag().getId()).toList())
                .forEach(tag -> tagById.put(tag.getId(), tag));

        Map<Long, List<ProfileTagSummaryResponse>> tagsByUserId = new HashMap<>();
        userProfileTags.forEach(userProfileTag -> {
            ProfileTag tag = tagById.get(userProfileTag.getTag().getId());
            if (tag != null && tag.getTagType() == ProfileTag.TagType.SKILL) {
                tagsByUserId
                        .computeIfAbsent(userProfileTag.getProfile().getUserId(), ignored -> new ArrayList<>())
                        .add(ProfileTagSummaryResponse.from(tag));
            }
        });
        return tagsByUserId;
    }

    private Map<Long, List<ProfileTagSummaryResponse>> findPortfolioSkillTagsByItemId(
            List<PortfolioItem> portfolioItems
    ) {
        if (portfolioItems.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<ProfileTagSummaryResponse>> tagsByPortfolioItemId = new HashMap<>();
        portfolioItemSkillTagRepository.findAllByPortfolioItem_IdIn(
                        portfolioItems.stream().map(PortfolioItem::getId).toList()
                )
                .forEach(link -> tagsByPortfolioItemId
                        .computeIfAbsent(link.getPortfolioItem().getId(), ignored -> new ArrayList<>())
                        .add(ProfileTagSummaryResponse.from(link.getTag())));
        return tagsByPortfolioItemId;
    }

    private Map<Long, List<ProfileCodeNameResponse>> findPortfolioRolesByItemId(List<PortfolioItem> portfolioItems) {
        if (portfolioItems.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<ProfileCodeNameResponse>> rolesByPortfolioItemId = new HashMap<>();
        portfolioItemRoleRepository.findAllByPortfolioItem_IdIn(
                        portfolioItems.stream().map(PortfolioItem::getId).toList()
                ).stream()
                .sorted(java.util.Comparator.comparingInt(PortfolioItemRole::getSelectionOrder))
                .forEach(link -> rolesByPortfolioItemId
                        .computeIfAbsent(link.getPortfolioItem().getId(), ignored -> new ArrayList<>())
                        .add(ProfileCodeNameResponse.from(link.getRole())));
        return rolesByPortfolioItemId;
    }

    private PublicProfileResponse toPublicProfileResponse(
            UserProfile profile,
            User user,
            List<ProfileTagSummaryResponse> skills
    ) {
        List<ProfileCodeNameResponse> primaryRoles = findPrimaryRoles(readPrimaryRoleCodes(profile)).stream()
                .sorted(java.util.Comparator.comparingInt(ProfileRole.PrimaryRole::getDisplayOrder))
                .map(ProfileCodeNameResponse::from)
                .toList();

        return new PublicProfileResponse(
                profile.getUserId(),
                user.getNickname(),
                user.getUniversityVerifiedAt() != null,
                profile.getProfileImageUrl(),
                profile.getActivityStatus(),
                profile.getActivityStatus().getDisplayName(),
                primaryRoles,
                profile.getDepartment().getDisplayName(),
                profile.getGrade(),
                profile.getBio(),
                skills
        );
    }

    private PublicProfileListResponse toPublicProfileListResponse(
            Page<UserProfile> profilePage,
            List<PublicProfileResponse> profiles
    ) {
        return new PublicProfileListResponse(
                profiles,
                profilePage.getNumber(),
                profilePage.getSize(),
                profilePage.getTotalElements(),
                profilePage.getTotalPages(),
                profilePage.hasNext()
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

    private <T> T readJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 프로필 JSON 데이터를 읽을 수 없습니다.", exception);
        }
    }
}
