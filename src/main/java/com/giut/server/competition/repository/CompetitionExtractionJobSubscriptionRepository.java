package com.giut.server.competition.repository;

import com.giut.server.competition.entity.CompetitionExtractionJobSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CompetitionExtractionJobSubscriptionRepository
        extends JpaRepository<CompetitionExtractionJobSubscription, Long> {

    boolean existsByJob_IdAndUser_Id(Long jobId, Long userId);

    long countByJob_Id(Long jobId);

    /** 작업을 기다리는 회원(처음 접수한 회원 + 같은 URL로 합류한 회원). 완료 알림 대상이다. */
    @Query("select s.user.id from CompetitionExtractionJobSubscription s where s.job.id = :jobId order by s.id")
    List<Long> findUserIdsByJobId(@Param("jobId") Long jobId);

    /** 작업마다 [작업 id, 구독 회원 수]를 돌려준다. 관리자 실패 목록에서 한 페이지 분량을 한 번에 센다. */
    @Query("""
            select s.job.id, count(s) from CompetitionExtractionJobSubscription s
            where s.job.id in :jobIds group by s.job.id
            """)
    List<Object[]> countByJobIds(@Param("jobIds") Collection<Long> jobIds);
}
