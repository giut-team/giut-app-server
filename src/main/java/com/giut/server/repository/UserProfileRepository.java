package com.giut.server.repository;

import com.giut.server.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    List<UserProfile> findAllBySearchableTrueAndActivityStatusNot(UserProfile.ActivityStatus activityStatus);
}
