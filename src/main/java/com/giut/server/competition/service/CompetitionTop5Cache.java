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
import java.time.Instant;
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
        // 기동 중 요청이 먼저 들어와 loadInitialSnapshot()이 이미 채웠다면 중복 로딩하지 않는다.
        if (snapshot.get() == null) refresh();
    }

    @Scheduled(initialDelay = 300_000, fixedRate = 300_000)
    public void refresh() {
        // tryLock: 이미 갱신 중이면(겹친 스케줄 실행, 또는 기동 시 warmUp과 첫 요청이 동시에 탐) 대기하지 않고 그냥 건너뛴다.
        if (!refreshLock.tryLock()) return;
        try {
            snapshot.set(List.copyOf(competitionService.getTop5Competitions()));
        } catch (RuntimeException e) {
            // 집계 쿼리 실패 시 캐시를 비우지 않고 직전 성공 스냅샷을 그대로 유지한다.
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
                Competition.PublicationStatus.PUBLISHED, Instant.now()));
        return cached.stream().filter(item -> published.contains(item.id())).toList();
    }

    private List<PublicCompetitionResponse> loadInitialSnapshot() {
        // refresh()와 달리 여기선 블로킹 lock: 최초 로딩은 결과 없이 빈 값을 돌려줄 수 없어
        // 동시에 들어온 첫 요청들이 각자 DB를 때리지 않고 한 번의 로딩을 같이 기다리게 한다.
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
