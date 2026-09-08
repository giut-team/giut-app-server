package com.giut.server.repository;

import com.giut.server.entity.ProfileRoleSkillTag;
import com.giut.server.entity.ProfileRoleSkillTagId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProfileRoleSkillTagRepository extends JpaRepository<ProfileRoleSkillTag, ProfileRoleSkillTagId> {

    @EntityGraph(attributePaths = {"tag", "role"})
    List<ProfileRoleSkillTag> findAllByTag_IdIn(Collection<Long> tagIds);
}
