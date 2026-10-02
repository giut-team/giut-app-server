package com.giut.server.service;

import com.giut.server.dto.profile.request.UpsertPortfolioItemRequest;
import com.giut.server.dto.profile.request.PortfolioShowcaseRequest;
import com.giut.server.entity.PortfolioItem;
import com.giut.server.entity.User;
import com.giut.server.repository.PortfolioItemRepository;
import com.giut.server.repository.PortfolioItemSkillTagRepository;
import com.giut.server.repository.ProfileTagRepository;
import com.giut.server.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioItemServiceTest {

    @Mock private PortfolioItemRepository portfolioItemRepository;
    @Mock private PortfolioItemSkillTagRepository portfolioItemSkillTagRepository;
    @Mock private ProfileTagRepository profileTagRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private PortfolioItemService portfolioItemService;

    @Test
    void createPlacesNewItemFirstAndHidesPreviousSixth() {
        User user = User.createOAuthUser("member@example.com", "회원", User.OAuthProvider.KAKAO, "kakao-12");
        List<PortfolioItem> existing = new ArrayList<>();
        for (int order = 1; order <= 6; order++) {
            PortfolioItem item = PortfolioItem.create(
                    user, "https://example.com/image.png", "기존 프로젝트", "설명",
                    null, null, null, "## 소개"
            );
            item.changeShowcaseOrder(order);
            existing.add(item);
        }
        existing.get(5).makeRepresentative();

        when(userRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(user));
        when(profileTagRepository.findAllById(any())).thenReturn(List.of());
        when(portfolioItemRepository.findAllByUser_IdAndShowcaseOrderIsNotNullOrderByShowcaseOrderAsc(12L))
                .thenReturn(existing);
        when(portfolioItemRepository.save(any(PortfolioItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpsertPortfolioItemRequest request = new UpsertPortfolioItemRequest(
                "https://example.com/new.png", "새 프로젝트", "설명",
                null, null, 5, "## 새 소개", List.of()
        );

        var response = portfolioItemService.createPortfolioItem(12L, request);

        assertThat(response.showcaseOrder()).isEqualTo(1);
        assertThat(response.representative()).isFalse();
        for (int index = 0; index < 5; index++) {
            assertThat(existing.get(index).getShowcaseOrder()).isEqualTo(index + 2);
        }
        assertThat(existing.get(5).getShowcaseOrder()).isNull();
        assertThat(existing.get(5).isRepresentative()).isFalse();
    }

    @Test
    void emptyShowcaseListIsAllowed() {
        when(portfolioItemRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(12L))
                .thenReturn(List.of());

        var response = portfolioItemService.changePortfolioShowcase(12L, new PortfolioShowcaseRequest(List.of()));

        assertThat(response.portfolioItems()).isEmpty();
        assertThat(response.representativePortfolioItemId()).isNull();
    }
}
