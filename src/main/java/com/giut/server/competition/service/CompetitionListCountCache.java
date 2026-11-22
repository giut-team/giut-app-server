package com.giut.server.competition.service;

import com.giut.server.competition.dto.response.CompetitionRecruitmentStatus;
import com.giut.server.competition.entity.Competition;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * 공개 공모전 목록의 전체 개수를 필터 조합별로 잠깐 들고 있는 메모리 캐시.
 *
 * <p>부하 측정(performance/results/mysql-competition-load-2026-11-19.md)에서 목록 {@code COUNT}가
 * DB 시간의 약 68%였다. 앱이 전체 개수를 보여주므로 {@code COUNT}를 없애지 않고 캐시한다.
 *
 * <ul>
 *   <li>개수는 TTL 동안 실제와 다를 수 있다. 새로 공개된 공모전은 늦게 더해지고, 마감이 지난 공모전은 늦게 빠진다.</li>
 *   <li>관리자 변경 때 바로 지우지 않는다. 개수가 바뀌는 경로(관리자 생성·수정·보관, 검증 후 공개, AI 배치,
 *       회원 제출)가 많고, 마감이 지나 줄어드는 개수는 어차피 TTL로만 맞출 수 있기 때문이다.</li>
 *   <li>검색어 조건은 경우의 수가 끝이 없어 이 캐시를 쓰지 않는다(호출하는 쪽에서 거른다).</li>
 *   <li>서버마다 따로 들고 있다. 서버를 여러 대로 늘리면 서버 사이 개수가 최대 TTL만큼 다를 수 있다.</li>
 * </ul>
 *
 * <p>TTL이 0이면 캐시하지 않고 매번 센다. 테스트는 이 설정으로 기존 개수 기대값을 그대로 확인한다.
 */
@Component
public class CompetitionListCountCache {

    // 카테고리 × 활동 유형 × 모집 상태(각각 null 포함) 조합보다 넉넉한 상한이다.
    private static final long MAXIMUM_SIZE = 1_000;

    private final Cache<Key, Long> cache;

    @Autowired
    public CompetitionListCountCache(@Value("${giut.competition.list-count-cache.ttl:1m}") Duration ttl) {
        this(ttl, Ticker.systemTicker());
    }

    CompetitionListCountCache(Duration ttl, Ticker ticker) {
        if (ttl.isNegative()) {
            throw new IllegalArgumentException("giut.competition.list-count-cache.ttl은 0 이상이어야 합니다.");
        }
        this.cache = ttl.isZero() ? null : Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(MAXIMUM_SIZE)
                .ticker(ticker)
                .build();
    }

    /**
     * 캐시된 개수를 돌려주고, 없거나 만료됐으면 {@code counter}로 세어 저장한다.
     * 같은 키를 동시에 요청하면 한 번만 세고 나머지는 그 결과를 기다린다. 세다가 실패하면 저장하지 않는다.
     */
    public long get(Key key, LongSupplier counter) {
        if (cache == null) {
            return counter.getAsLong();
        }
        return cache.get(key, ignored -> counter.getAsLong());
    }

    /** 검색어를 뺀 목록 필터 조합. 정렬은 개수와 상관없어 넣지 않는다. */
    public record Key(
            Competition.Category category,
            Competition.ActivityType activityType,
            CompetitionRecruitmentStatus status
    ) {
    }
}
