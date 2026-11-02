package com.giut.server.competition.repository;

import com.giut.server.competition.entity.CompetitionScrap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CompetitionScrapRepository extends JpaRepository<CompetitionScrap, Long> {

    boolean existsByUser_IdAndCompetition_Id(Long userId, Long competitionId);

    long countByCompetition_Id(Long competitionId);

    long countByUser_Id(Long userId);

    void deleteByUser_IdAndCompetition_Id(Long userId, Long competitionId);

    /** scrapped(내가 스크랩했는지)와 scrapCount(전체 수)를 한 번의 조회로 함께 얻는다. */
    @Query("""
            select count(scrap.id) as scrapCount,
                   coalesce(sum(case when scrap.user.id = :userId then 1 else 0 end), 0) > 0 as scrapped
            from CompetitionScrap scrap
            where scrap.competition.id = :competitionId
            """)
    CompetitionScrapSummaryProjection findScrapSummary(
            @Param("userId") Long userId,
            @Param("competitionId") Long competitionId
    );

    @Query("""
            select scrap.competition.id as competitionId, count(scrap.id) as scrapCount
            from CompetitionScrap scrap
            where scrap.competition.id in :competitionIds
            group by scrap.competition.id
            """)
    List<CompetitionScrapCountProjection> countByCompetitionIdIn(
            @Param("competitionIds") Collection<Long> competitionIds
    );
}
