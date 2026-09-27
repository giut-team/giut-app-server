package com.giut.server.repository;

import com.giut.server.entity.CompetitionVerificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionVerificationLogRepository extends JpaRepository<CompetitionVerificationLog, Long> {
}
