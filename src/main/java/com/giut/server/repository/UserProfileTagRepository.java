package com.giut.server.repository;

import com.giut.server.entity.UserProfileTag;
import com.giut.server.entity.UserProfileTagId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UserProfileTagRepository extends JpaRepository<UserProfileTag, UserProfileTagId> {

    List<UserProfileTag> findAllByUserId(Long userId);

    List<UserProfileTag> findAllByUserIdIn(Collection<Long> userIds);

    void deleteByUserId(Long userId);
}
