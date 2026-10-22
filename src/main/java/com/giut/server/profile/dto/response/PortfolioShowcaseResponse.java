package com.giut.server.profile.dto.response;

import com.giut.server.profile.dto.common.PortfolioItemDto;

import java.util.List;

public record PortfolioShowcaseResponse(
        List<PortfolioItemDto> portfolioItems,
        Long representativePortfolioItemId
) {
}
