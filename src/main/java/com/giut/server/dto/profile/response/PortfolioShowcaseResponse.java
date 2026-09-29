package com.giut.server.dto.profile.response;

import com.giut.server.dto.profile.common.PortfolioItemDto;

import java.util.List;

public record PortfolioShowcaseResponse(
        List<PortfolioItemDto> portfolioItems,
        Long representativePortfolioItemId
) {
}
