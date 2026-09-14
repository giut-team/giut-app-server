package com.giut.server.repository;

import com.giut.server.entity.CompetitionScrap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CompetitionScrapRepository extends JpaRepository<CompetitionScrap, Long> {

    boolean existsByUser_IdAndCompetition_Id(Long userId, Long competitionId);

    long countByCompetition_Id(Long competitionId);

    void deleteByUser_IdAndCompetition_Id(Long userId, Long competitionId);

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
