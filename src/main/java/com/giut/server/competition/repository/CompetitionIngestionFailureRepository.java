package com.giut.server.competition.repository;

import com.giut.server.competition.entity.CompetitionIngestionFailure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CompetitionIngestionFailureRepository extends JpaRepository<CompetitionIngestionFailure, Long>,
        JpaSpecificationExecutor<CompetitionIngestionFailure> {
}
