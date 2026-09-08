package com.giut.server.repository;

import com.giut.server.entity.ProfileRoleCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfileRoleCategoryRepository extends JpaRepository<ProfileRoleCategory, Long> {

    Optional<ProfileRoleCategory> findByCode(String code);
}
