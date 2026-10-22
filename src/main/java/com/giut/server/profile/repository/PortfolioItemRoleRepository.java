package com.giut.server.profile.repository;

import com.giut.server.profile.entity.PortfolioItemRole;
import com.giut.server.profile.entity.PortfolioItemRoleId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PortfolioItemRoleRepository extends JpaRepository<PortfolioItemRole, PortfolioItemRoleId> {

    @EntityGraph(attributePaths = {"portfolioItem", "role"})
    List<PortfolioItemRole> findAllByPortfolioItem_IdIn(Collection<Long> portfolioItemIds);

    void deleteByPortfolioItem_Id(Long portfolioItemId);
}
