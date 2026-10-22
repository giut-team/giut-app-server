package com.giut.server.profile.repository;

import com.giut.server.profile.entity.ActivityHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityHistoryRepository extends JpaRepository<ActivityHistory, Long> {

    List<ActivityHistory> findAllByUser_IdOrderByStartMonthDescEndMonthDescIdDesc(Long userId);

    Optional<ActivityHistory> findByIdAndUser_Id(Long activityHistoryId, Long userId);

    void deleteByUser_Id(Long userId);
}
