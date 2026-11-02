package com.giut.server.competition.repository;

import com.giut.server.competition.entity.CompetitionUrl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CompetitionUrlRepository extends JpaRepository<CompetitionUrl, Long> {

    List<CompetitionUrl> findAllByCompetition_Id(Long competitionId);

    List<CompetitionUrl> findAllByCompetition_IdInOrderByIdAsc(Collection<Long> competitionIds);

    List<CompetitionUrl> findAllByCompetition_IdInAndPrimaryTrue(Collection<Long> competitionIds);

    List<CompetitionUrl> findAllByOfficialUrlHashIn(Collection<String> officialUrlHashes);

    void deleteByCompetition_Id(Long competitionId);
}
