package com.giut.server.profile.service;

import com.giut.server.profile.dto.common.ProfileCodeNameResponse;
import com.giut.server.profile.dto.request.UpsertPortfolioItemRequest;
import com.giut.server.profile.dto.request.PortfolioShowcaseRequest;
import com.giut.server.profile.entity.PortfolioItem;
import com.giut.server.profile.entity.ProfileRole;
import com.giut.server.user.entity.User;
import com.giut.server.profile.repository.PortfolioItemRepository;
import com.giut.server.profile.repository.PortfolioItemRoleRepository;
import com.giut.server.profile.repository.PortfolioItemSkillTagRepository;
import com.giut.server.profile.repository.ProfileRoleRepository;
import com.giut.server.profile.repository.ProfileTagRepository;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioItemServiceTest {

    @Mock private PortfolioItemRepository portfolioItemRepository;
    @Mock private PortfolioItemSkillTagRepository portfolioItemSkillTagRepository;
    @Mock private PortfolioItemRoleRepository portfolioItemRoleRepository;
    @Mock private ProfileTagRepository profileTagRepository;
    @Mock private ProfileRoleRepository profileRoleRepository;
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
        ProfileRole dataAnalyst = role("DATA_ANALYST", "데이터 분석");
        ProfileRole backendDeveloper = role("BACKEND_DEVELOPER", "백엔드 개발자");

        when(userRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(user));
        when(profileTagRepository.findAllById(any())).thenReturn(List.of());
        when(profileRoleRepository.findAllByCodeIn(List.of("BACKEND_DEVELOPER", "DATA_ANALYST")))
                .thenReturn(List.of(dataAnalyst, backendDeveloper));
        when(portfolioItemRepository.findAllByUser_IdAndShowcaseOrderIsNotNullOrderByShowcaseOrderAsc(12L))
                .thenReturn(existing);
        when(portfolioItemRepository.save(any(PortfolioItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpsertPortfolioItemRequest request = new UpsertPortfolioItemRequest(
                "https://example.com/new.png", "새 프로젝트", "설명",
                null, null, 5, List.of("BACKEND_DEVELOPER", "DATA_ANALYST"), "## 새 소개", List.of()
        );

        var response = portfolioItemService.createPortfolioItem(12L, request);

        assertThat(response.showcaseOrder()).isEqualTo(1);
        assertThat(response.roles()).extracting(ProfileCodeNameResponse::code)
                .containsExactly("BACKEND_DEVELOPER", "DATA_ANALYST");
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

    @Test
    void updateReplacesRoles() {
        PortfolioItem item = PortfolioItem.create(
                null, "https://example.com/image.png", "프로젝트", "설명",
                null, null, 3, "## 소개"
        );
        org.springframework.test.util.ReflectionTestUtils.setField(item, "id", 5L);
        ProfileRole backendDeveloper = role("BACKEND_DEVELOPER", "백엔드 개발자");
        when(portfolioItemRepository.findByIdAndUser_Id(5L, 12L)).thenReturn(Optional.of(item));
        when(profileTagRepository.findAllById(any())).thenReturn(List.of());
        when(profileRoleRepository.findAllByCodeIn(List.of("BACKEND_DEVELOPER")))
                .thenReturn(List.of(backendDeveloper));

        UpsertPortfolioItemRequest request = new UpsertPortfolioItemRequest(
                "https://example.com/image.png", "프로젝트", "설명",
                null, null, 3, List.of("BACKEND_DEVELOPER"), "## 소개", List.of()
        );

        var response = portfolioItemService.updatePortfolioItem(12L, 5L, request);

        assertThat(response.roles()).extracting(ProfileCodeNameResponse::code)
                .containsExactly("BACKEND_DEVELOPER");
        verify(portfolioItemRoleRepository).deleteByPortfolioItem_Id(5L);
    }

    @Test
    void rejectsUnknownRoleCode() {
        PortfolioItem item = PortfolioItem.create(
                null, "https://example.com/image.png", "프로젝트", "설명",
                null, null, 3, "## 소개"
        );
        when(portfolioItemRepository.findByIdAndUser_Id(5L, 12L)).thenReturn(Optional.of(item));
        when(profileTagRepository.findAllById(any())).thenReturn(List.of());
        when(profileRoleRepository.findAllByCodeIn(List.of("UNKNOWN"))).thenReturn(List.of());
        UpsertPortfolioItemRequest request = new UpsertPortfolioItemRequest(
                "https://example.com/image.png", "프로젝트", "설명",
                null, null, 3, List.of("UNKNOWN"), "## 소개", List.of()
        );

        assertThatThrownBy(() -> portfolioItemService.updatePortfolioItem(12L, 5L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 세부 역할");
    }

    @Test
    void deleteRemovesRoleAndSkillTagLinksBeforePortfolioItem() {
        PortfolioItem item = PortfolioItem.create(
                null, "https://example.com/image.png", "프로젝트", "설명",
                null, null, 3, "## 소개"
        );
        when(portfolioItemRepository.findByIdAndUser_Id(5L, 12L)).thenReturn(Optional.of(item));
        when(portfolioItemRepository.findAllByUser_IdAndShowcaseOrderIsNotNullOrderByShowcaseOrderAsc(12L))
                .thenReturn(List.of());

        portfolioItemService.deletePortfolioItem(12L, 5L);

        var inOrder = org.mockito.Mockito.inOrder(portfolioItemRoleRepository,
                portfolioItemSkillTagRepository, portfolioItemRepository);
        inOrder.verify(portfolioItemRoleRepository).deleteByPortfolioItem_Id(5L);
        inOrder.verify(portfolioItemRoleRepository).flush();
        inOrder.verify(portfolioItemSkillTagRepository).deleteByPortfolioItem_Id(5L);
        inOrder.verify(portfolioItemSkillTagRepository).flush();
        inOrder.verify(portfolioItemRepository).delete(item);
    }

    private ProfileRole role(String code, String name) {
        ProfileRole role = mock(ProfileRole.class);
        when(role.getCode()).thenReturn(code);
        when(role.getName()).thenReturn(name);
        return role;
    }
}
