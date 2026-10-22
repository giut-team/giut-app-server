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

    @Query("""
            select case when count(b) > 0 then true else false end from UserBlock b
            where (b.blocker.id = :first and b.blockedUser.id = :second)
               or (b.blocker.id = :second and b.blockedUser.id = :first)
            """)
    boolean existsBetween(@Param("first") Long first, @Param("second") Long second);
}
