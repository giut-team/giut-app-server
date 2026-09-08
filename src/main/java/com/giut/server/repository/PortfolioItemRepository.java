package com.giut.server.repository;

import com.giut.server.entity.PortfolioItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, Long> {

    List<PortfolioItem> findAllByUserIdOrderByDisplayOrderAsc(Long userId);

    Optional<PortfolioItem> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
