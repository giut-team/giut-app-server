package com.giut.server.repository;

import com.giut.server.entity.ProfileLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfileLinkRepository extends JpaRepository<ProfileLink, Long> {

    List<ProfileLink> findAllByUserIdOrderByIdAsc(Long userId);

    void deleteByUserId(Long userId);
}
