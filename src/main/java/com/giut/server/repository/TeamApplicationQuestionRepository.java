package com.giut.server.repository;

import com.giut.server.entity.TeamApplicationQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamApplicationQuestionRepository extends JpaRepository<TeamApplicationQuestion, Long> {

    List<TeamApplicationQuestion> findAllByTeamIdAndStatusOrderByDisplayOrderAsc(
            Long teamId,
            TeamApplicationQuestion.Status status
    );
}
