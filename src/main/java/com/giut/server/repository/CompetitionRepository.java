package com.giut.server.repository;

import com.giut.server.entity.Competition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompetitionRepository extends JpaRepository<Competition, Long>, JpaSpecificationExecutor<Competition> {

    Optional<Competition> findByIdAndPublicationStatus(Long id, Competition.PublicationStatus publicationStatus);

    @Query(value = """
            SELECT competition.*
            FROM competitions competition
            LEFT JOIN competition_scraps scrap ON scrap.competition_id = competition.id
            WHERE competition.publication_status = :publicationStatus
            GROUP BY competition.id
            ORDER BY (0.3 * LOG(1 + competition.view_count)
                     + 0.7 * LOG(1 + COUNT(scrap.id))) DESC,
                     competition.view_count DESC,
                     COUNT(scrap.id) DESC,
                     competition.id DESC
            LIMIT 5
            """, nativeQuery = true)
    List<Competition> findTop5ByPopularity(@Param("publicationStatus") String publicationStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Competition competition set competition.viewCount = competition.viewCount + 1 where competition.id = :competitionId")
    int incrementViewCount(@Param("competitionId") Long competitionId);
}
