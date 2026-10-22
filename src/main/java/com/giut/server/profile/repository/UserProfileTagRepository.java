package com.giut.server.profile.repository;

import com.giut.server.profile.entity.UserProfileTag;
import com.giut.server.profile.entity.UserProfileTagId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UserProfileTagRepository extends JpaRepository<UserProfileTag, UserProfileTagId> {

    @EntityGraph(attributePaths = {"profile", "tag"})
    List<UserProfileTag> findAllByProfile_UserId(Long userId);

    @EntityGraph(attributePaths = {"profile", "tag"})
    List<UserProfileTag> findAllByProfile_UserIdIn(Collection<Long> userIds);

    void deleteByProfile_UserId(Long userId);
}
