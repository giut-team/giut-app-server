package com.giut.server.service;

import com.giut.server.dto.profile.common.ActivityHistoryDto;
import com.giut.server.entity.ActivityHistory;
import com.giut.server.entity.User;
import com.giut.server.repository.ActivityHistoryRepository;
import com.giut.server.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityHistoryServiceTest {

    @Mock private ActivityHistoryRepository activityHistoryRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private ActivityHistoryService activityHistoryService;

    @Test
    void emptyListDeletesAllHistories() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(12L);

        activityHistoryService.replaceForProfile(user, List.of());

        verify(activityHistoryRepository).deleteByUser_Id(12L);
        verify(activityHistoryRepository).flush();
        verify(activityHistoryRepository, never()).saveAll(anyList());
    }

    @Test
    void nonEmptyListReplacesHistories() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(12L);
        ActivityHistoryDto award = new ActivityHistoryDto(
                null, ActivityHistory.Category.AWARD, null, "수상", "서울특별시",
                YearMonth.of(2025, 3), YearMonth.of(2025, 6)
        );

        activityHistoryService.replaceForProfile(user, List.of(award));

        var order = org.mockito.Mockito.inOrder(activityHistoryRepository);
        order.verify(activityHistoryRepository).deleteByUser_Id(12L);
        order.verify(activityHistoryRepository).flush();
        order.verify(activityHistoryRepository).saveAll(anyList());
    }

    @Test
    void invalidPeriodDoesNotDeleteExistingHistories() {
        User user = org.mockito.Mockito.mock(User.class);
        ActivityHistoryDto invalid = new ActivityHistoryDto(
                null, ActivityHistory.Category.AWARD, null, "수상", "서울특별시",
                YearMonth.of(2025, 6), YearMonth.of(2025, 3)
        );

        assertThatThrownBy(() -> activityHistoryService.replaceForProfile(user, List.of(invalid)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(activityHistoryRepository, never()).deleteByUser_Id(org.mockito.ArgumentMatchers.anyLong());
    }
}
