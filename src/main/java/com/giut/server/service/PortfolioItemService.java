package com.giut.server.service;

import com.giut.server.dto.profile.request.PortfolioShowcaseRequest;
import com.giut.server.dto.profile.response.PortfolioItemListResponse;
import com.giut.server.dto.profile.response.PortfolioShowcaseResponse;
import com.giut.server.dto.profile.common.PortfolioItemDto;
import com.giut.server.dto.profile.response.RepresentativePortfolioResponse;
import com.giut.server.entity.PortfolioItem;
import com.giut.server.entity.PortfolioItemSkillTag;
import com.giut.server.entity.ProfileTag;
import com.giut.server.entity.User;
import com.giut.server.dto.profile.response.ProfileTagSummaryResponse;
import com.giut.server.exception.ResourceNotFoundException;
import com.giut.server.repository.PortfolioItemRepository;
import com.giut.server.repository.PortfolioItemSkillTagRepository;
import com.giut.server.repository.ProfileTagRepository;
import com.giut.server.repository.UserRepository;
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

    private final ProfileTagRepository profileTagRepository;

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PortfolioItemListResponse getMyPortfolioItems(Long userId) {
        return toListResponse(portfolioItemRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(userId));
    }

    @Transactional
    public PortfolioItemDto createPortfolioItem(Long userId, PortfolioItemDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        validateProjectPeriod(request);
        List<ProfileTag> skillTags = findSkillTags(request.skillTagIds());
        PortfolioItem portfolioItem = portfolioItemRepository.save(PortfolioItem.create(
                user,
                request.imageUrl(),
                request.title(),
                request.caption(),
                request.projectStartDate(),
                request.projectEndDate(),
                request.teamSize(),
                request.markdownContent()
        ));

        replaceSkillTags(portfolioItem, skillTags);
        return PortfolioItemDto.from(portfolioItem, skillTags.stream().map(ProfileTagSummaryResponse::from).toList());
    }

    @Transactional
    public PortfolioItemDto updatePortfolioItem(Long userId, Long portfolioItemId, PortfolioItemDto request) {
        PortfolioItem portfolioItem = findPortfolioItem(userId, portfolioItemId);
        validateProjectPeriod(request);
        List<ProfileTag> skillTags = findSkillTags(request.skillTagIds());
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

        return PortfolioItemDto.from(portfolioItem, skillTags.stream().map(ProfileTagSummaryResponse::from).toList());
    }

    @Transactional
    public void deletePortfolioItem(Long userId, Long portfolioItemId) {
        portfolioItemRepository.delete(findPortfolioItem(userId, portfolioItemId));
        portfolioItemRepository.flush();
        reassignShowcaseOrder(userId);
    }

    @Transactional
    public PortfolioShowcaseResponse changePortfolioShowcase(Long userId, PortfolioShowcaseRequest request) {
        List<PortfolioItem> allPortfolioItems = portfolioItemRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(userId);
        List<Long> requestedIds = request.portfolioItemIds();
        validateDistinctOwnedPortfolioIds(allPortfolioItems, requestedIds);

        Map<Long, PortfolioItem> itemById = allPortfolioItems.stream()
                .collect(java.util.stream.Collectors.toMap(PortfolioItem::getId, item -> item));

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
        return portfolioItems.stream()
                .map(portfolioItem -> PortfolioItemDto.from(
                        portfolioItem,
                        skillTagsByPortfolioItemId.getOrDefault(portfolioItem.getId(), List.of())
                ))
                .toList();
    }

    private void validateDistinctOwnedPortfolioIds(List<PortfolioItem> portfolioItems, List<Long> requestedIds) {
        if (requestedIds.contains(null)) {
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

    private void validateProjectPeriod(PortfolioItemDto request) {
        if (request.projectStartDate() != null
                && request.projectEndDate() != null
                && request.projectEndDate().isBefore(request.projectStartDate())) {
            throw new IllegalArgumentException("프로젝트 종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    private List<ProfileTag> findSkillTags(List<Long> skillTagIds) {
        List<Long> requestedIds = skillTagIds == null ? List.of() : skillTagIds;
        if (requestedIds.contains(null)) {
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

        Map<Long, ProfileTag> tagById = new HashMap<>();
        tags.forEach(tag -> tagById.put(tag.getId(), tag));
        return requestedIds.stream().map(tagById::get).toList();
    }

    private void replaceSkillTags(PortfolioItem portfolioItem, List<ProfileTag> skillTags) {
        portfolioItemSkillTagRepository.deleteByPortfolioItem_Id(portfolioItem.getId());
        portfolioItemSkillTagRepository.flush();
        portfolioItemSkillTagRepository.saveAll(skillTags.stream()
                .map(tag -> PortfolioItemSkillTag.create(portfolioItem, tag))
                .toList());
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
