package com.giut.server.competition.repository;

import com.giut.server.competition.entity.CompetitionVerificationLog;

public interface CompetitionCreationEvidenceProjection {

    Long getCompetitionId();

    CompetitionVerificationLog.Method getMethod();
}
