package com.giut.server.repository;

import com.giut.server.entity.ProfileTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfileTagRepository extends JpaRepository<ProfileTag, Long> {

    Optional<ProfileTag> findByTagTypeAndNormalizedName(ProfileTag.TagType tagType, String normalizedName);

    java.util.List<ProfileTag> findAllByTagTypeOrderByIdAsc(ProfileTag.TagType tagType);
}
