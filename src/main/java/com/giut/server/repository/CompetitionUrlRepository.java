package com.giut.server.repository;

import com.giut.server.entity.CompetitionUrl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompetitionUrlRepository extends JpaRepository<CompetitionUrl, Long> {

    List<CompetitionUrl> findAllByCompetition_Id(Long competitionId);

    List<CompetitionUrl> findAllByCompetition_IdInAndPrimaryTrue(Collection<Long> competitionIds);

    Optional<CompetitionUrl> findByNormalizedUrlHash(String normalizedUrlHash);

    void deleteByCompetition_Id(Long competitionId);
}
