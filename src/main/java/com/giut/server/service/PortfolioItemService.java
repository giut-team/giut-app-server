package com.giut.server.service;

import com.giut.server.dto.request.PortfolioItemOrderRequest;
import com.giut.server.dto.request.PortfolioItemRequest;
import com.giut.server.dto.response.PortfolioItemListResponse;
import com.giut.server.dto.response.PortfolioItemResponse;
import com.giut.server.entity.PortfolioItem;
import com.giut.server.exception.ResourceNotFoundException;
import com.giut.server.repository.PortfolioItemRepository;
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

    @Transactional(readOnly = true)
    public PortfolioItemListResponse getMyPortfolioItems(Long userId) {
        return toListResponse(portfolioItemRepository.findAllByUserIdOrderByDisplayOrderAsc(userId));
    }

    @Transactional
    public PortfolioItemResponse createPortfolioItem(Long userId, PortfolioItemRequest request) {
        if (portfolioItemRepository.countByUserId(userId) >= MAX_PORTFOLIO_ITEMS) {
            throw new IllegalStateException("포트폴리오는 최대 6개까지 등록할 수 있습니다.");
        }

        int displayOrder = portfolioItemRepository.findAllByUserIdOrderByDisplayOrderAsc(userId).size() + 1;
        PortfolioItem portfolioItem = portfolioItemRepository.save(PortfolioItem.create(
                userId,
                request.imageUrl(),
                request.title(),
                request.caption(),
                request.content(),
                displayOrder
        ));

        return PortfolioItemResponse.from(portfolioItem);
    }

    @Transactional
    public PortfolioItemResponse updatePortfolioItem(Long userId, Long portfolioItemId, PortfolioItemRequest request) {
        PortfolioItem portfolioItem = findPortfolioItem(userId, portfolioItemId);
        portfolioItem.update(request.imageUrl(), request.title(), request.caption(), request.content());

        return PortfolioItemResponse.from(portfolioItem);
    }

    @Transactional
    public void deletePortfolioItem(Long userId, Long portfolioItemId) {
        portfolioItemRepository.delete(findPortfolioItem(userId, portfolioItemId));
        portfolioItemRepository.flush();
        reassignDisplayOrder(userId);
    }

    @Transactional
    public PortfolioItemListResponse changePortfolioItemOrder(Long userId, PortfolioItemOrderRequest request) {
        List<PortfolioItem> portfolioItems = portfolioItemRepository.findAllByUserIdOrderByDisplayOrderAsc(userId);
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
        return portfolioItemRepository.findByIdAndUserId(portfolioItemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("포트폴리오 항목을 찾을 수 없습니다."));
    }

    private void reassignDisplayOrder(Long userId) {
        List<PortfolioItem> portfolioItems = portfolioItemRepository.findAllByUserIdOrderByDisplayOrderAsc(userId);
        for (int index = 0; index < portfolioItems.size(); index++) {
            portfolioItems.get(index).changeDisplayOrder(index + 1);
        }
    }

    private PortfolioItemListResponse toListResponse(List<PortfolioItem> portfolioItems) {
        return new PortfolioItemListResponse(portfolioItems.stream()
                .map(PortfolioItemResponse::from)
                .toList());
    }
}
