package com.giut.server.competition.service;

import com.giut.server.competition.dto.response.PublicCompetitionResponse;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.repository.CompetitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/** Caches the expensive ranking while checking publication status on each request. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitionTop5Cache {
    private final CompetitionService competitionService;
    private final CompetitionRepository competitionRepository;
    private final AtomicReference<List<PublicCompetitionResponse>> snapshot = new AtomicReference<>();
    private final ReentrantLock refreshLock = new ReentrantLock();

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (snapshot.get() == null) refresh();
    }

    @Scheduled(initialDelay = 300_000, fixedRate = 300_000)
    public void refresh() {
        if (!refreshLock.tryLock()) return;
        try {
            snapshot.set(List.copyOf(competitionService.getTop5Competitions()));
        } catch (RuntimeException e) {
            log.warn("인기 공모전 캐시 갱신 실패: 기존 결과를 유지합니다.", e);
        } finally {
            refreshLock.unlock();
        }
    }

    public List<PublicCompetitionResponse> getPublishedTop5() {
        List<PublicCompetitionResponse> cached = snapshot.get();
        if (cached == null) cached = loadInitialSnapshot();
        if (cached.isEmpty()) return cached;

        // Query current DB state: do not expose unpublished/deleted entries from a stale snapshot.
        Set<Long> published = Set.copyOf(competitionRepository.findPublishedIds(
                cached.stream().map(PublicCompetitionResponse::id).toList(),
                Competition.PublicationStatus.PUBLISHED));
        return cached.stream().filter(item -> published.contains(item.id())).toList();
    }

    private List<PublicCompetitionResponse> loadInitialSnapshot() {
        refreshLock.lock();
        try {
            List<PublicCompetitionResponse> cached = snapshot.get();
            if (cached == null) {
                cached = List.copyOf(competitionService.getTop5Competitions());
                snapshot.set(cached);
            }
            return cached;
        } finally {
            refreshLock.unlock();
        }
    }
}
