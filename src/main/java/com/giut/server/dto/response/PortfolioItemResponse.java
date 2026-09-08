package com.giut.server.dto.response;

import com.giut.server.entity.PortfolioItem;

public record PortfolioItemResponse(
        Long id,
        String imageUrl,
        String title,
        String caption,
        String content,
        int displayOrder
) {
    public static PortfolioItemResponse from(PortfolioItem portfolioItem) {
        return new PortfolioItemResponse(
                portfolioItem.getId(),
                portfolioItem.getImageUrl(),
                portfolioItem.getTitle(),
                portfolioItem.getCaption(),
                portfolioItem.getContent(),
                portfolioItem.getDisplayOrder()
        );
    }
}
