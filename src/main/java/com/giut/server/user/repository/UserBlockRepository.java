package com.giut.server.user.repository;

import com.giut.server.user.entity.UserBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
    boolean existsByBlocker_IdAndBlockedUser_Id(Long blockerId, Long blockedId);
    void deleteByBlocker_IdAndBlockedUser_Id(Long blockerId, Long blockedId);

    @EntityGraph(attributePaths = "blockedUser")
    Page<UserBlock> findAllByBlocker_Id(Long blockerId, Pageable pageable);

    // 차단 자체는 방향성이 있는 행(uk_user_blocks_pair)이지만, 노출 여부 판단은 어느 쪽이
    // 차단했든 상호 비공개로 취급해야 해서 두 방향을 모두 확인한다.
    @Query("""
            select case when count(b) > 0 then true else false end from UserBlock b
            where (b.blocker.id = :first and b.blockedUser.id = :second)
               or (b.blocker.id = :second and b.blockedUser.id = :first)
            """)
    boolean existsBetween(@Param("first") Long first, @Param("second") Long second);
}
