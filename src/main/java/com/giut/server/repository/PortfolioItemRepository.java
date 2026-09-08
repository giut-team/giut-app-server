package com.giut.server.repository;

import com.giut.server.entity.PortfolioItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, Long> {

    List<PortfolioItem> findAllByUser_IdOrderByDisplayOrderAsc(Long userId);

    Optional<PortfolioItem> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_Id(Long userId);
}
