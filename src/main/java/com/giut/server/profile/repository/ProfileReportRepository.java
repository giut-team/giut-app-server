package com.giut.server.profile.repository;

import com.giut.server.profile.entity.ProfileReport;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProfileReportRepository extends JpaRepository<ProfileReport, Long> {

    boolean existsByReporter_IdAndReportedProfile_UserIdAndStatus(
            Long reporterUserId, Long reportedProfileUserId, ProfileReport.Status status
    );

    Page<ProfileReport> findAllByStatus(ProfileReport.Status status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ProfileReport r where r.id = :reportId")
    Optional<ProfileReport> findByIdForUpdate(@Param("reportId") Long reportId);
}
