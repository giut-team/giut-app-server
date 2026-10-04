package com.giut.server.profile.dto.response;

import java.util.List;
import com.giut.server.profile.dto.common.PortfolioItemDto;

public record PortfolioItemListResponse(
        List<PortfolioItemDto> portfolioItems
) {
}
