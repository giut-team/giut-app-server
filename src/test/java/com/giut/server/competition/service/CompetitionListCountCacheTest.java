package com.giut.server.competition.service;

import com.giut.server.competition.dto.response.CompetitionRecruitmentStatus;
import com.giut.server.competition.entity.Competition;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompetitionListCountCacheTest {

    private static final CompetitionListCountCache.Key NO_FILTER = new CompetitionListCountCache.Key(null, null, null, null);
    private static final CompetitionListCountCache.Key IT_OPEN = new CompetitionListCountCache.Key(
            Competition.Category.WEB_MOBILE_IT, null, CompetitionRecruitmentStatus.OPEN, null);

    private final FakeTicker ticker = new FakeTicker();
    private final AtomicLong counts = new AtomicLong();

    @Test
    void countsOnceWithinTtlAndRecountsAfterExpiry() {
        CompetitionListCountCache cache = new CompetitionListCountCache(Duration.ofMinutes(1), ticker);

        assertThat(cache.get(NO_FILTER, () -> count(10))).isEqualTo(10);
        ticker.advance(Duration.ofSeconds(59));
        assertThat(cache.get(NO_FILTER, () -> count(20))).isEqualTo(10);
        assertThat(counts).hasValue(1);

        ticker.advance(Duration.ofSeconds(1));
        assertThat(cache.get(NO_FILTER, () -> count(20))).isEqualTo(20);
        assertThat(counts).hasValue(2);
    }

    @Test
    void keepsEachFilterCombinationSeparately() {
        CompetitionListCountCache cache = new CompetitionListCountCache(Duration.ofMinutes(1), ticker);

        assertThat(cache.get(NO_FILTER, () -> count(10))).isEqualTo(10);
        assertThat(cache.get(IT_OPEN, () -> count(3))).isEqualTo(3);
        assertThat(cache.get(new CompetitionListCountCache.Key(
                Competition.Category.WEB_MOBILE_IT, null, CompetitionRecruitmentStatus.OPEN, null), () -> count(99)))
                .isEqualTo(3);
        assertThat(counts).hasValue(2);
    }

    @Test
    void sharesKeywordCountIgnoringCaseButKeepsOtherKeywordsSeparately() {
        CompetitionListCountCache cache = new CompetitionListCountCache(Duration.ofMinutes(1), ticker);

        assertThat(cache.get(keyword("AI"), () -> count(7))).isEqualTo(7);
        assertThat(cache.get(keyword("ai"), () -> count(99))).isEqualTo(7);
        assertThat(cache.get(keyword("데이터"), () -> count(4))).isEqualTo(4);
        assertThat(cache.get(NO_FILTER, () -> count(10))).isEqualTo(10);
        assertThat(counts).hasValue(3);
    }

    @Test
    void zeroTtlCountsEveryTime() {
        CompetitionListCountCache cache = new CompetitionListCountCache(Duration.ZERO, ticker);

        assertThat(cache.get(NO_FILTER, () -> count(10))).isEqualTo(10);
        assertThat(cache.get(NO_FILTER, () -> count(11))).isEqualTo(11);
        assertThat(counts).hasValue(2);
    }

    @Test
    void failedCountIsNotCached() {
        CompetitionListCountCache cache = new CompetitionListCountCache(Duration.ofMinutes(1), ticker);

        assertThatThrownBy(() -> cache.get(NO_FILTER, () -> {
            throw new IllegalStateException("DB 오류");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(cache.get(NO_FILTER, () -> count(10))).isEqualTo(10);
        assertThat(counts).hasValue(1);
    }

    @Test
    void rejectsNegativeTtl() {
        assertThatThrownBy(() -> new CompetitionListCountCache(Duration.ofSeconds(-1), ticker))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CompetitionListCountCache.Key keyword(String keyword) {
        return new CompetitionListCountCache.Key(null, null, null, keyword);
    }

    private long count(long value) {
        counts.incrementAndGet();
        return value;
    }

    private static final class FakeTicker implements Ticker {
        private long nanos;

        @Override
        public long read() {
            return nanos;
        }

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }
    }
}
