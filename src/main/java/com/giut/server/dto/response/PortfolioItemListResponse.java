package com.giut.server.dto.response;

import java.util.List;

public record PortfolioItemListResponse(
        List<PortfolioItemResponse> portfolioItems
) {
}
