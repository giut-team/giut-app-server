package com.giut.server.repository;

import com.giut.server.entity.TeamApplicationAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TeamApplicationAnswerRepository extends JpaRepository<TeamApplicationAnswer, Long> {

    List<TeamApplicationAnswer> findAllByApplicationId(Long applicationId);

    List<TeamApplicationAnswer> findAllByApplicationIdIn(Collection<Long> applicationIds);
}
