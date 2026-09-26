package com.giut.server.repository;

import com.giut.server.entity.ActivityHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityHistoryRepository extends JpaRepository<ActivityHistory, Long> {

    List<ActivityHistory> findAllByUser_IdOrderByStartMonthDescEndMonthDescIdDesc(Long userId);

    Optional<ActivityHistory> findByIdAndUser_Id(Long activityHistoryId, Long userId);
}
