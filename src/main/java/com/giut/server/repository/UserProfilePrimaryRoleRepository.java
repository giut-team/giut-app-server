package com.giut.server.repository;

import com.giut.server.entity.UserProfilePrimaryRole;
import com.giut.server.entity.UserProfilePrimaryRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserProfilePrimaryRoleRepository extends JpaRepository<UserProfilePrimaryRole, UserProfilePrimaryRoleId> {

    List<UserProfilePrimaryRole> findAllByUserId(Long userId);

    void deleteByUserId(Long userId);
}
