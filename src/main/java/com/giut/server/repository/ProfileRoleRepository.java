package com.giut.server.repository;

import com.giut.server.entity.ProfileRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProfileRoleRepository extends JpaRepository<ProfileRole, Long> {

    List<ProfileRole> findAllByCodeIn(Collection<String> codes);

    List<ProfileRole> findAllByPrimaryRoleOrderByDisplayOrderAsc(ProfileRole.PrimaryRole primaryRole);
}
