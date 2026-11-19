package com.giut.server.competition.repository;

import com.giut.server.competition.entity.Competition;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface CompetitionRepository extends JpaRepository<Competition, Long>, JpaSpecificationExecutor<Competition> {

    Optional<Competition> findBySubmissionSourceUrlHash(String submissionSourceUrlHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select competition from Competition competition where competition.id = :id")
    Optional<Competition> findByIdForUpdate(@Param("id") Long id);

    Optional<Competition> findByIdAndPublicationStatus(Long id, Competition.PublicationStatus publicationStatus);

    // 스크랩 수를 공모전별로 따로 세지 않고 LEFT JOIN + GROUP BY 한 번으로 집계해서 N+1을 피한다.
    // 로그 스케일(0.3*view + 0.7*scrap 가중치)은 조회수가 스크랩보다 쉽게 늘어나는 걸 완화하려는 것.
    // 실측: 공모전 5만건·스크랩 55만건 기준 이 쿼리 1회 약 162~259ms — 그래서 Top5Cache로 캐싱한다.
    @Query(value = """
            SELECT competition.*
            FROM competitions competition
            LEFT JOIN competition_scraps scrap ON scrap.competition_id = competition.id
            WHERE competition.publication_status = :publicationStatus
              AND competition.application_end_at > :now
            GROUP BY competition.id
            ORDER BY (0.3 * LOG(1 + competition.view_count)
                     + 0.7 * LOG(1 + COUNT(scrap.id))) DESC,
                     competition.view_count DESC,
                     COUNT(scrap.id) DESC,
                     competition.id DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Competition> findTop10ByPopularity(@Param("publicationStatus") String publicationStatus, @Param("now") Instant now);

    @Query("select c.id from Competition c where c.id in :ids and c.publicationStatus = :status and c.applicationEndAt > :now")
    List<Long> findPublishedIds(@Param("ids") List<Long> ids,
                                @Param("status") Competition.PublicationStatus status, @Param("now") Instant now);

    // 벌크 UPDATE는 영속성 컨텍스트를 거치지 않아 이미 로딩된 Competition이 있으면 값이 안 맞게(stale)
    // 보일 수 있다. clearAutomatically로 컨텍스트를 비워 이후 조회가 DB의 최신 값을 다시 읽게 한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Competition competition set competition.viewCount = competition.viewCount + 1
            where competition.id = :competitionId and competition.publicationStatus = :publicationStatus
            """)
    int incrementViewCount(@Param("competitionId") Long competitionId,
                           @Param("publicationStatus") Competition.PublicationStatus publicationStatus);

    /**
     * 스크랩 행을 실제로 추가한 트랜잭션에서 호출한다. 읽고-더하고-쓰는 대신 DB에서 원자적으로 더해
     * 서로 다른 회원의 동시 스크랩에도 값이 유실되지 않는다. 영속성 컨텍스트는 비우지 않는다(같은 트랜잭션에서 다시 읽지 않음).
     */
    @Modifying(flushAutomatically = true)
    @Query("update Competition competition set competition.scrapCount = competition.scrapCount + 1 where competition.id = :competitionId")
    int incrementScrapCount(@Param("competitionId") Long competitionId);

    /** 스크랩 행을 실제로 삭제했을 때만 호출한다. 어떤 경우에도 음수가 되지 않게 한다. */
    @Modifying(flushAutomatically = true)
    @Query("update Competition competition set competition.scrapCount = competition.scrapCount - 1 "
            + "where competition.id = :competitionId and competition.scrapCount > 0")
    int decrementScrapCount(@Param("competitionId") Long competitionId);
}
