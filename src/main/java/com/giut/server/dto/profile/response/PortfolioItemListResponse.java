package com.giut.server.dto.profile.response;

import java.util.List;
import com.giut.server.dto.profile.common.PortfolioItemDto;

public record PortfolioItemListResponse(
        List<PortfolioItemDto> portfolioItems
) {
}
