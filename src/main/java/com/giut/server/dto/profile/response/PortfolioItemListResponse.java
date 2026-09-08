package com.giut.server.dto.profile.response;

import java.util.List;

public record PortfolioItemListResponse(
        List<PortfolioItemResponse> portfolioItems
) {
}
