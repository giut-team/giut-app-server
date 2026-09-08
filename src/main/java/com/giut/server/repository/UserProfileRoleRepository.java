package com.giut.server.repository;

import com.giut.server.entity.UserProfileRole;
import com.giut.server.entity.UserProfileRoleId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserProfileRoleRepository extends JpaRepository<UserProfileRole, UserProfileRoleId> {

    @EntityGraph(attributePaths = "role")
    List<UserProfileRole> findAllByUserId(Long userId);

    void deleteByUserId(Long userId);
}
