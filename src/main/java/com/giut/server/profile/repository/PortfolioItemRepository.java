package com.giut.server.profile.repository;

import com.giut.server.profile.entity.PortfolioItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, Long> {

    List<PortfolioItem> findAllByUser_IdOrderByCreatedAtDescIdDesc(Long userId);

    List<PortfolioItem> findAllByUser_IdAndShowcaseOrderIsNotNullOrderByShowcaseOrderAsc(Long userId);

    Optional<PortfolioItem> findByIdAndUser_Id(Long id, Long userId);

    /** 전체·대표 작품 수를 한 번의 조회로 집계한다(프로필 요약 조회 쿼리 수 절감). */
    @Query("""
            select count(item) as total,
                   coalesce(sum(case when item.showcaseOrder is not null then 1 else 0 end), 0) as showcased
            from PortfolioItem item
            where item.user.id = :userId
            """)
    PortfolioCountProjection countSummaryByUserId(@Param("userId") Long userId);
}
