package com.giut.server.competition.repository;

import com.giut.server.competition.entity.CompetitionVerificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionVerificationLogRepository extends JpaRepository<CompetitionVerificationLog, Long> {
}
