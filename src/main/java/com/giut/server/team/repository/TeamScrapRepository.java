package com.giut.server.team.repository;

import com.giut.server.team.entity.TeamScrap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TeamScrapRepository extends JpaRepository<TeamScrap, Long> {

    long countByUser_Id(Long userId);

    boolean existsByUser_IdAndTeam_Id(Long userId, Long teamId);

    @Query("select scrap.team.id from TeamScrap scrap where scrap.user.id = :userId and scrap.team.id in :teamIds")
    List<Long> findScrappedTeamIds(
            @Param("userId") Long userId,
            @Param("teamIds") Collection<Long> teamIds
    );

    void deleteByUser_IdAndTeam_Id(Long userId, Long teamId);
}
