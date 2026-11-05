package com.giut.server.profile.service;

import com.giut.server.profile.dto.request.PortfolioShowcaseRequest;
import com.giut.server.profile.dto.request.UpsertPortfolioItemRequest;
import com.giut.server.profile.dto.response.PortfolioItemListResponse;
import com.giut.server.profile.dto.response.PortfolioShowcaseResponse;
import com.giut.server.profile.dto.common.PortfolioItemDto;
import com.giut.server.profile.dto.common.ProfileCodeNameResponse;
import com.giut.server.profile.dto.response.RepresentativePortfolioResponse;
import com.giut.server.profile.entity.PortfolioItem;
import com.giut.server.profile.entity.PortfolioItemRole;
import com.giut.server.profile.entity.PortfolioItemSkillTag;
import com.giut.server.profile.entity.ProfileRole;
import com.giut.server.profile.entity.ProfileTag;
import com.giut.server.user.entity.User;
import com.giut.server.profile.dto.response.ProfileTagSummaryResponse;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.profile.repository.PortfolioItemRepository;
import com.giut.server.profile.repository.PortfolioItemRoleRepository;
import com.giut.server.profile.repository.PortfolioItemSkillTagRepository;
import com.giut.server.profile.repository.ProfileRoleRepository;
import com.giut.server.profile.repository.ProfileTagRepository;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PortfolioItemService {

    private final PortfolioItemRepository portfolioItemRepository;

    private final PortfolioItemSkillTagRepository portfolioItemSkillTagRepository;

    private final PortfolioItemRoleRepository portfolioItemRoleRepository;

    private final ProfileTagRepository profileTagRepository;

    private final ProfileRoleRepository profileRoleRepository;

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PortfolioItemListResponse getMyPortfolioItems(Long userId) {
        return toListResponse(portfolioItemRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(userId));
    }

    @Transactional
    public PortfolioItemDto createPortfolioItem(Long userId, UpsertPortfolioItemRequest request) {
        // 포트폴리오 행 자체에는 자연스러운 락 대상이 없어서, User 행을 사용자 범위의 임시 뮤텍스로 빌려 쓴다.
        // 아래 쇼케이스 순서 재배치는 같은 사용자의 여러 행을 한꺼번에 읽고 다시 쓰므로 동시 요청 시 꼬일 수 있다.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        validateProjectPeriod(request);
        List<ProfileTag> skillTags = findSkillTags(request.skillTagIds());
        List<ProfileRole> roles = findRoles(request.roles());
        List<PortfolioItem> showcaseItems = portfolioItemRepository
                .findAllByUser_IdAndShowcaseOrderIsNotNullOrderByShowcaseOrderAsc(userId);
        // 새 항목이 1번을 차지하므로 기존 1~5위는 2~6위로 한 칸씩 밀고, 6위 이하였던 항목은 쇼케이스에서 뺀다.
        // (쇼케이스 전체 용량은 새 항목 포함 6개)
        for (int index = showcaseItems.size() - 1; index >= 0; index--) {
            showcaseItems.get(index).changeShowcaseOrder(index < 5 ? index + 2 : null);
        }

        PortfolioItem newItem = PortfolioItem.create(
                user,
                request.imageUrl(),
                request.title(),
                request.caption(),
                request.projectStartDate(),
                request.projectEndDate(),
                request.teamSize(),
                request.markdownContent()
        );
        newItem.changeShowcaseOrder(1);
        PortfolioItem portfolioItem = portfolioItemRepository.save(newItem);

        replaceSkillTags(portfolioItem, skillTags);
        replaceRoles(portfolioItem, roles);
        return PortfolioItemDto.from(
                portfolioItem,
                skillTags.stream().map(ProfileTagSummaryResponse::from).toList(),
                roles.stream().map(ProfileCodeNameResponse::from).toList()
        );
    }

    @Transactional
    public PortfolioItemDto updatePortfolioItem(Long userId, Long portfolioItemId, UpsertPortfolioItemRequest request) {
        PortfolioItem portfolioItem = findPortfolioItem(userId, portfolioItemId);
        validateProjectPeriod(request);
        List<ProfileTag> skillTags = findSkillTags(request.skillTagIds());
        List<ProfileRole> roles = findRoles(request.roles());
        portfolioItem.update(
                request.imageUrl(),
                request.title(),
                request.caption(),
                request.projectStartDate(),
                request.projectEndDate(),
                request.teamSize(),
                request.markdownContent()
        );
        replaceSkillTags(portfolioItem, skillTags);
        replaceRoles(portfolioItem, roles);

        return PortfolioItemDto.from(
                portfolioItem,
                skillTags.stream().map(ProfileTagSummaryResponse::from).toList(),
                roles.stream().map(ProfileCodeNameResponse::from).toList()
        );
    }

    @Transactional
    public void deletePortfolioItem(Long userId, Long portfolioItemId) {
        PortfolioItem portfolioItem = findPortfolioItem(userId, portfolioItemId);
        // 자식(역할·스킬태그 연결)부터 지워야 부모 삭제 시 FK 제약에 걸리지 않는다. 각 delete 뒤의 flush는
        // derived delete가 지연 실행되는 걸 막아, 다음 단계가 꼬이지 않게 순서를 강제한다.
        portfolioItemRoleRepository.deleteByPortfolioItem_Id(portfolioItemId);
        portfolioItemRoleRepository.flush();
        portfolioItemSkillTagRepository.deleteByPortfolioItem_Id(portfolioItemId);
        portfolioItemSkillTagRepository.flush();
        portfolioItemRepository.delete(portfolioItem);
        portfolioItemRepository.flush();
        // 삭제로 쇼케이스 순위에 빈 자리가 생길 수 있어 1부터 다시 채운다.
        reassignShowcaseOrder(userId);
    }

    @Transactional
    public PortfolioShowcaseResponse changePortfolioShowcase(Long userId, PortfolioShowcaseRequest request) {
        List<PortfolioItem> allPortfolioItems = portfolioItemRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(userId);
        List<Long> requestedIds = request.portfolioItemIds();
        // 소유·중복·null 검증을 먼저 끝내두므로, 아래 루프에서 requestedIds에 있는 id는 indexOf가 항상 유효한
        // 위치를 찾는다는 게 보장된다.
        validateDistinctOwnedPortfolioIds(allPortfolioItems, requestedIds);

        Map<Long, PortfolioItem> itemById = allPortfolioItems.stream()
                .collect(java.util.stream.Collectors.toMap(PortfolioItem::getId, item -> item));

        // 기존 쇼케이스 상태는 보지 않고, 요청받은 목록을 그대로 새 정답으로 삼아 전체를 덮어쓴다(증분 수정이 아닌 전체 교체).
        // 요청에 없는 항목은 index가 -1이 되어 쇼케이스에서 빠진다.
        for (PortfolioItem portfolioItem : allPortfolioItems) {
            int index = requestedIds.indexOf(portfolioItem.getId());
            portfolioItem.changeShowcaseOrder(index < 0 ? null : index + 1);
        }

        List<PortfolioItem> showcaseItems = requestedIds.stream().map(itemById::get).toList();
        return toShowcaseResponse(showcaseItems);
    }

    @Transactional
    public RepresentativePortfolioResponse setRepresentativePortfolio(Long userId, Long portfolioItemId) {
        List<PortfolioItem> portfolioItems = portfolioItemRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(userId);
        PortfolioItem selectedItem = portfolioItems.stream()
                .filter(item -> item.getId().equals(portfolioItemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("포트폴리오 항목을 찾을 수 없습니다."));

        if (selectedItem.getShowcaseOrder() == null) {
            throw new IllegalArgumentException("공개 프로필에 노출 중인 포트폴리오만 대표로 지정할 수 있습니다.");
        }

        portfolioItems.forEach(PortfolioItem::clearRepresentative);
        selectedItem.makeRepresentative();
        return new RepresentativePortfolioResponse(selectedItem.getId(), true);
    }

    private PortfolioItem findPortfolioItem(Long userId, Long portfolioItemId) {
        return portfolioItemRepository.findByIdAndUser_Id(portfolioItemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("포트폴리오 항목을 찾을 수 없습니다."));
    }

    private void reassignShowcaseOrder(Long userId) {
        List<PortfolioItem> showcaseItems = portfolioItemRepository
                .findAllByUser_IdAndShowcaseOrderIsNotNullOrderByShowcaseOrderAsc(userId);
        for (int index = 0; index < showcaseItems.size(); index++) {
            showcaseItems.get(index).changeShowcaseOrder(index + 1);
        }
    }

    private PortfolioItemListResponse toListResponse(List<PortfolioItem> portfolioItems) {
        return new PortfolioItemListResponse(toDtos(portfolioItems));
    }

    private PortfolioShowcaseResponse toShowcaseResponse(List<PortfolioItem> showcaseItems) {
        Long representativePortfolioItemId = showcaseItems.stream()
                .filter(PortfolioItem::isRepresentative)
                .map(PortfolioItem::getId)
                .findFirst()
                .orElse(null);
        return new PortfolioShowcaseResponse(toDtos(showcaseItems), representativePortfolioItemId);
    }

    private List<PortfolioItemDto> toDtos(List<PortfolioItem> portfolioItems) {
        Map<Long, List<ProfileTagSummaryResponse>> skillTagsByPortfolioItemId = findSkillTagsByPortfolioItemId(portfolioItems);
        Map<Long, List<ProfileCodeNameResponse>> rolesByPortfolioItemId = findRolesByPortfolioItemId(portfolioItems);
        return portfolioItems.stream()
                .map(portfolioItem -> PortfolioItemDto.from(
                        portfolioItem,
                        skillTagsByPortfolioItemId.getOrDefault(portfolioItem.getId(), List.of()),
                        rolesByPortfolioItemId.getOrDefault(portfolioItem.getId(), List.of())
                ))
                .toList();
    }

    private void validateDistinctOwnedPortfolioIds(List<PortfolioItem> portfolioItems, List<Long> requestedIds) {
        if (requestedIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("공개 포트폴리오 ID에는 null을 넣을 수 없습니다.");
        }
        Set<Long> requestedIdSet = new HashSet<>(requestedIds);
        if (requestedIdSet.size() != requestedIds.size()) {
            throw new IllegalArgumentException("공개 포트폴리오는 중복해서 선택할 수 없습니다.");
        }

        Set<Long> ownedIdSet = portfolioItems.stream()
                .map(PortfolioItem::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!ownedIdSet.containsAll(requestedIdSet)) {
            throw new IllegalArgumentException("내 포트폴리오만 공개 목록에 추가할 수 있습니다.");
        }
    }

    private void validateProjectPeriod(UpsertPortfolioItemRequest request) {
        if (request.projectStartDate() != null
                && request.projectEndDate() != null
                && request.projectEndDate().isBefore(request.projectStartDate())) {
            throw new IllegalArgumentException("프로젝트 종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    private List<ProfileTag> findSkillTags(List<Long> skillTagIds) {
        List<Long> requestedIds = skillTagIds == null ? List.of() : skillTagIds;
        if (requestedIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("기술 스택 태그 ID에는 null을 넣을 수 없습니다.");
        }

        Set<Long> distinctIds = new LinkedHashSet<>(requestedIds);
        if (distinctIds.size() != requestedIds.size()) {
            throw new IllegalArgumentException("기술 스택 태그는 중복해서 선택할 수 없습니다.");
        }

        List<ProfileTag> tags = profileTagRepository.findAllById(distinctIds);
        if (tags.size() != distinctIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 기술 스택 태그가 포함되어 있습니다.");
        }
        if (tags.stream().anyMatch(tag -> tag.getTagType() != ProfileTag.TagType.SKILL)) {
            throw new IllegalArgumentException("기술 스택 태그만 선택할 수 있습니다.");
        }

        // findAllById는 요청 순서를 보장하지 않으므로, 맵으로 바꾼 뒤 requestedIds 순서대로 다시 매핑한다.
        Map<Long, ProfileTag> tagById = new HashMap<>();
        tags.forEach(tag -> tagById.put(tag.getId(), tag));
        return requestedIds.stream().map(tagById::get).toList();
    }

    private List<ProfileRole> findRoles(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new IllegalArgumentException("포트폴리오 역할은 1개 이상 선택해야 합니다.");
        }
        if (roleCodes.size() > 3) {
            throw new IllegalArgumentException("포트폴리오 역할은 최대 3개까지 선택할 수 있습니다.");
        }
        if (roleCodes.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw new IllegalArgumentException("역할 코드는 비어 있을 수 없습니다.");
        }
        if (new HashSet<>(roleCodes).size() != roleCodes.size()) {
            throw new IllegalArgumentException("포트폴리오 역할은 중복해서 선택할 수 없습니다.");
        }

        List<ProfileRole> roles = profileRoleRepository.findAllByCodeIn(roleCodes);
        if (roles.size() != roleCodes.size()) {
            throw new IllegalArgumentException("지원하지 않는 세부 역할이 포함되어 있습니다.");
        }
        // skillTags와 같은 이유로 조회 결과를 요청 순서에 다시 맞춘다. 이 순서가 replaceRoles에서
        // selectionOrder로 저장되어 포트폴리오에 표시되는 역할 순서가 된다.
        Map<String, ProfileRole> roleByCode = new HashMap<>();
        roles.forEach(role -> roleByCode.put(role.getCode(), role));
        return roleCodes.stream().map(roleByCode::get).toList();
    }

    private void replaceSkillTags(PortfolioItem portfolioItem, List<ProfileTag> skillTags) {
        // derived delete는 다음 flush까지 지연되므로, 뒤이은 insert보다 먼저 DB에 반영되도록 명시적으로 flush한다.
        portfolioItemSkillTagRepository.deleteByPortfolioItem_Id(portfolioItem.getId());
        portfolioItemSkillTagRepository.flush();
        portfolioItemSkillTagRepository.saveAll(skillTags.stream()
                .map(tag -> PortfolioItemSkillTag.create(portfolioItem, tag))
                .toList());
    }

    private void replaceRoles(PortfolioItem portfolioItem, List<ProfileRole> roles) {
        // replaceSkillTags와 같은 이유로 delete 직후 flush한다.
        portfolioItemRoleRepository.deleteByPortfolioItem_Id(portfolioItem.getId());
        portfolioItemRoleRepository.flush();
        List<PortfolioItemRole> links = new java.util.ArrayList<>();
        for (int index = 0; index < roles.size(); index++) {
            links.add(PortfolioItemRole.create(portfolioItem, roles.get(index), index + 1));
        }
        portfolioItemRoleRepository.saveAll(links);
    }

    private Map<Long, List<ProfileCodeNameResponse>> findRolesByPortfolioItemId(List<PortfolioItem> portfolioItems) {
        if (portfolioItems.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<ProfileCodeNameResponse>> rolesByPortfolioItemId = new HashMap<>();
        portfolioItemRoleRepository.findAllByPortfolioItem_IdIn(
                        portfolioItems.stream().map(PortfolioItem::getId).toList()
                ).stream()
                .sorted(java.util.Comparator.comparingInt(PortfolioItemRole::getSelectionOrder))
                .forEach(link -> rolesByPortfolioItemId
                        .computeIfAbsent(link.getPortfolioItem().getId(), ignored -> new java.util.ArrayList<>())
                        .add(ProfileCodeNameResponse.from(link.getRole())));
        return rolesByPortfolioItemId;
    }

    private Map<Long, List<ProfileTagSummaryResponse>> findSkillTagsByPortfolioItemId(
            List<PortfolioItem> portfolioItems
    ) {
        if (portfolioItems.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<ProfileTagSummaryResponse>> skillTagsByPortfolioItemId = new HashMap<>();
        portfolioItemSkillTagRepository.findAllByPortfolioItem_IdIn(
                        portfolioItems.stream().map(PortfolioItem::getId).toList()
                )
                .forEach(link -> skillTagsByPortfolioItemId
                        .computeIfAbsent(link.getPortfolioItem().getId(), ignored -> new java.util.ArrayList<>())
                        .add(ProfileTagSummaryResponse.from(link.getTag())));
        return skillTagsByPortfolioItemId;
    }
}
