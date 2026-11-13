package com.giut.server.competition.repository;

import com.giut.server.competition.entity.CompetitionExtractionJobSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionExtractionJobSubscriptionRepository
        extends JpaRepository<CompetitionExtractionJobSubscription, Long> {

    boolean existsByJob_IdAndUser_Id(Long jobId, Long userId);
}
