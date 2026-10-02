package com.giut.server.repository;

import com.giut.server.entity.TeamScrap;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamScrapRepository extends JpaRepository<TeamScrap, Long> {

    long countByUser_Id(Long userId);

    boolean existsByUser_IdAndTeam_Id(Long userId, Long teamId);

    void deleteByUser_IdAndTeam_Id(Long userId, Long teamId);
}
