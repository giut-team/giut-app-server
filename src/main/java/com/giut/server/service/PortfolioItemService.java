package com.giut.server.service;

import com.giut.server.dto.profile.request.PortfolioItemOrderRequest;
import com.giut.server.dto.profile.response.PortfolioItemListResponse;
import com.giut.server.dto.profile.common.PortfolioItemDto;
import com.giut.server.entity.PortfolioItem;
import com.giut.server.entity.User;
import com.giut.server.exception.ResourceNotFoundException;
import com.giut.server.repository.PortfolioItemRepository;
import com.giut.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PortfolioItemService {

    private static final int MAX_PORTFOLIO_ITEMS = 6;

    private final PortfolioItemRepository portfolioItemRepository;

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PortfolioItemListResponse getMyPortfolioItems(Long userId) {
        return toListResponse(portfolioItemRepository.findAllByUser_IdOrderByDisplayOrderAsc(userId));
    }

    @Transactional
    public PortfolioItemDto createPortfolioItem(Long userId, PortfolioItemDto request) {
        if (portfolioItemRepository.countByUser_Id(userId) >= MAX_PORTFOLIO_ITEMS) {
            throw new IllegalStateException("포트폴리오는 최대 6개까지 등록할 수 있습니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        int displayOrder = portfolioItemRepository.findAllByUser_IdOrderByDisplayOrderAsc(userId).size() + 1;
        PortfolioItem portfolioItem = portfolioItemRepository.save(PortfolioItem.create(
                user,
                request.imageUrl(),
                request.title(),
                request.caption(),
                request.content(),
                displayOrder
        ));

        return PortfolioItemDto.from(portfolioItem);
    }

    @Transactional
    public PortfolioItemDto updatePortfolioItem(Long userId, Long portfolioItemId, PortfolioItemDto request) {
        PortfolioItem portfolioItem = findPortfolioItem(userId, portfolioItemId);
        portfolioItem.update(request.imageUrl(), request.title(), request.caption(), request.content());

        return PortfolioItemDto.from(portfolioItem);
    }

    @Transactional
    public void deletePortfolioItem(Long userId, Long portfolioItemId) {
        portfolioItemRepository.delete(findPortfolioItem(userId, portfolioItemId));
        portfolioItemRepository.flush();
        reassignDisplayOrder(userId);
    }

    @Transactional
    public PortfolioItemListResponse changePortfolioItemOrder(Long userId, PortfolioItemOrderRequest request) {
        List<PortfolioItem> portfolioItems = portfolioItemRepository.findAllByUser_IdOrderByDisplayOrderAsc(userId);
        List<Long> requestedIds = request.portfolioItemIds();
        Set<Long> requestedIdSet = new HashSet<>(requestedIds);
        Set<Long> currentIdSet = portfolioItems.stream().map(PortfolioItem::getId).collect(java.util.stream.Collectors.toSet());

        if (requestedIdSet.size() != requestedIds.size() || !requestedIdSet.equals(currentIdSet)) {
            throw new IllegalArgumentException("현재 포트폴리오 항목 전체를 중복 없이 전달해야 합니다.");
        }

        java.util.Map<Long, PortfolioItem> itemById = portfolioItems.stream()
                .collect(java.util.stream.Collectors.toMap(PortfolioItem::getId, item -> item));
        for (int index = 0; index < requestedIds.size(); index++) {
            itemById.get(requestedIds.get(index)).changeDisplayOrder(index + 1);
        }

        return toListResponse(requestedIds.stream().map(itemById::get).toList());
    }

    private PortfolioItem findPortfolioItem(Long userId, Long portfolioItemId) {
        return portfolioItemRepository.findByIdAndUser_Id(portfolioItemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("포트폴리오 항목을 찾을 수 없습니다."));
    }

    private void reassignDisplayOrder(Long userId) {
        List<PortfolioItem> portfolioItems = portfolioItemRepository.findAllByUser_IdOrderByDisplayOrderAsc(userId);
        for (int index = 0; index < portfolioItems.size(); index++) {
            portfolioItems.get(index).changeDisplayOrder(index + 1);
        }
    }

    private PortfolioItemListResponse toListResponse(List<PortfolioItem> portfolioItems) {
        return new PortfolioItemListResponse(portfolioItems.stream()
                .map(PortfolioItemDto::from)
                .toList());
    }
}
