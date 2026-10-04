package com.giut.server.profile.repository;

import com.giut.server.profile.entity.PortfolioItemSkillTag;
import com.giut.server.profile.entity.PortfolioItemSkillTagId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PortfolioItemSkillTagRepository extends JpaRepository<PortfolioItemSkillTag, PortfolioItemSkillTagId> {

    @EntityGraph(attributePaths = {"portfolioItem", "tag"})
    List<PortfolioItemSkillTag> findAllByPortfolioItem_IdIn(Collection<Long> portfolioItemIds);

    void deleteByPortfolioItem_Id(Long portfolioItemId);
}
