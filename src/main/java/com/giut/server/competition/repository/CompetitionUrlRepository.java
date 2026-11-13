package com.giut.server.competition.repository;

import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.entity.CompetitionUrl;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompetitionUrlRepository extends JpaRepository<CompetitionUrl, Long> {

    List<CompetitionUrl> findAllByCompetition_Id(Long competitionId);

    List<CompetitionUrl> findAllByCompetition_IdInOrderByIdAsc(Collection<Long> competitionIds);

    List<CompetitionUrl> findAllByCompetition_IdInAndPrimaryTrue(Collection<Long> competitionIds);

    List<CompetitionUrl> findAllByOfficialUrlHashIn(Collection<String> officialUrlHashes);

    Optional<CompetitionUrl> findFirstByNormalizedUrlHashOrderByIdAsc(String normalizedUrlHash);

    /** 링크가 속한 공모전을 함께 읽는다. 연관 객체를 지연 로딩하지 않아 트랜잭션 밖에서도 안전하다. */
    interface LinkedCompetition {
        String getUrl();

        Long getCompetitionId();

        Competition.PublicationStatus getPublicationStatus();
    }

    /** 종류와 무관하게 정규화 hash가 같은 저장 링크(가장 먼저 저장된 것부터). */
    @Query("""
            select u.url as url, c.id as competitionId, c.publicationStatus as publicationStatus
            from CompetitionUrl u join u.competition c
            where u.normalizedUrlHash = :hash
            order by u.id
            """)
    List<LinkedCompetition> findLinkedByNormalizedUrlHash(@Param("hash") String hash, Limit limit);

    /** 공식(OFFICIAL) 링크가 같은 공모전. 공식 링크는 공모전 하나에만 등록할 수 있으므로 hash마다 최대 한 건이다. */
    @Query("""
            select u.url as url, c.id as competitionId, c.publicationStatus as publicationStatus
            from CompetitionUrl u join u.competition c
            where u.officialUrlHash in :hashes
            order by c.id, u.id
            """)
    List<LinkedCompetition> findLinkedByOfficialUrlHashIn(@Param("hashes") Collection<String> hashes);

    void deleteByCompetition_Id(Long competitionId);
}
