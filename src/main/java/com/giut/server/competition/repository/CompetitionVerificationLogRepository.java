package com.giut.server.competition.repository;

import com.giut.server.competition.entity.CompetitionVerificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CompetitionVerificationLogRepository extends JpaRepository<CompetitionVerificationLog, Long> {

    /**
     * 생성 경로를 증명하는 로그만 공모전별로 모은다. 관리자 등록은 ADMIN_REVIEW+행위자 있음+관리자 등록 사유,
     * AI 등록은 AUTO_EXTRACTION+행위자 없음+AI 등록 사유이며 둘 다 fromStatus가 없는 최초 로그다.
     */
    @Query("""
            select distinct l.competition.id as competitionId, l.verificationMethod as method
            from CompetitionVerificationLog l
            where l.competition.id in :competitionIds
              and l.fromStatus is null
              and ((l.verificationMethod = :adminMethod and l.actorUser is not null and l.reason = :adminReason)
                or (l.verificationMethod = :aiMethod and l.actorUser is null and l.reason = :aiReason))
            """)
    List<CompetitionCreationEvidenceProjection> findCreationEvidence(
            @Param("competitionIds") Collection<Long> competitionIds,
            @Param("adminMethod") CompetitionVerificationLog.Method adminMethod,
            @Param("adminReason") String adminReason,
            @Param("aiMethod") CompetitionVerificationLog.Method aiMethod,
            @Param("aiReason") String aiReason
    );

    default List<CompetitionCreationEvidenceProjection> findCreationEvidence(Collection<Long> competitionIds) {
        return findCreationEvidence(competitionIds,
                CompetitionVerificationLog.Method.ADMIN_REVIEW, CompetitionVerificationLog.ADMIN_CREATION_REASON,
                CompetitionVerificationLog.Method.AUTO_EXTRACTION, CompetitionVerificationLog.AI_CREATION_REASON);
    }
}
