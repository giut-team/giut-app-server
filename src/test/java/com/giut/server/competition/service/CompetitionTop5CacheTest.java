package com.giut.server.competition.service;

import com.giut.server.competition.dto.response.PublicCompetitionResponse;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.repository.CompetitionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CompetitionTop5CacheTest {
    private final CompetitionService loader = mock(CompetitionService.class);
    private final CompetitionRepository repository = mock(CompetitionRepository.class);
    private final CompetitionTop5Cache cache = new CompetitionTop5Cache(loader, repository);

    @Test
    void cacheIsReusedAndUnpublishedEntriesAreFilteredImmediately() {
        var first = item(1L);
        var second = item(2L);
        when(loader.getTop5Competitions()).thenReturn(List.of(first, second));
        when(repository.findPublishedIds(eq(List.of(1L, 2L)), eq(Competition.PublicationStatus.PUBLISHED), any()))
                .thenReturn(List.of(1L, 2L), List.of(2L));
        cache.warmUp();
        assertThat(cache.getPublishedTop5()).containsExactly(first, second);
        assertThat(cache.getPublishedTop5()).containsExactly(second);
        verify(loader, times(1)).getTop5Competitions();
    }

    @Test
    void failedRefreshKeepsPreviousSnapshotAndNextRefreshCanUpdateIt() {
        var first = item(1L);
        var second = item(2L);
        when(loader.getTop5Competitions()).thenReturn(List.of(first))
                .thenThrow(new IllegalStateException("db failure")).thenReturn(List.of(second));
        when(repository.findPublishedIds(any(), eq(Competition.PublicationStatus.PUBLISHED), any()))
                .thenAnswer(call -> call.getArgument(0));
        cache.refresh();
        cache.refresh();
        assertThat(cache.getPublishedTop5()).containsExactly(first);
        cache.refresh();
        assertThat(cache.getPublishedTop5()).containsExactly(second);
    }

    @Test
    void initialFailureIsRetriedAndVisibilityFailureDoesNotReturnStaleData() {
        var first = item(1L);
        when(loader.getTop5Competitions()).thenThrow(new IllegalStateException("startup"))
                .thenReturn(List.of(first));
        when(repository.findPublishedIds(any(), eq(Competition.PublicationStatus.PUBLISHED), any()))
                .thenThrow(new IllegalStateException("status unavailable"));
        cache.warmUp();
        assertThatThrownBy(cache::getPublishedTop5).hasMessage("status unavailable");
        verify(loader, times(2)).getTop5Competitions();
    }

    @Test
    void emptyResultIsAValidSnapshot() {
        when(loader.getTop5Competitions()).thenReturn(List.of());
        cache.warmUp();
        assertThat(cache.getPublishedTop5()).isEmpty();
        verifyNoInteractions(repository);
        verify(loader, times(1)).getTop5Competitions();
    }

    @Test
    void simultaneousFirstRequestsUseOneLoad() throws Exception {
        var first = item(1L);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(loader.getTop5Competitions()).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
            return List.of(first);
        });
        when(repository.findPublishedIds(any(), eq(Competition.PublicationStatus.PUBLISHED), any()))
                .thenReturn(List.of(1L));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(cache::getPublishedTop5);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var two = pool.submit(cache::getPublishedTop5);
            release.countDown();
            assertThat(one.get(5, TimeUnit.SECONDS)).containsExactly(first);
            assertThat(two.get(5, TimeUnit.SECONDS)).containsExactly(first);
        }
        verify(loader, times(1)).getTop5Competitions();
    }

    private static PublicCompetitionResponse item(long id) {
        var item = Mockito.mock(PublicCompetitionResponse.class);
        when(item.id()).thenReturn(id);
        return item;
    }
}
