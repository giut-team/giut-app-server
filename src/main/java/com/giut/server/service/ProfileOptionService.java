package com.giut.server.service;

import com.giut.server.dto.response.ProfileRoleListResponse;
import com.giut.server.dto.response.ProfileRoleResponse;
import com.giut.server.dto.response.ProfileTagListResponse;
import com.giut.server.dto.response.ProfileTagResponse;
import com.giut.server.entity.ProfileRoleCategory;
import com.giut.server.entity.ProfileTag;
import com.giut.server.repository.ProfileRoleCategoryRepository;
import com.giut.server.repository.ProfileRoleRepository;
import com.giut.server.repository.ProfileTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileOptionService {

    private final ProfileRoleCategoryRepository profileRoleCategoryRepository;

    private final ProfileRoleRepository profileRoleRepository;

    private final ProfileTagRepository profileTagRepository;

    @Transactional(readOnly = true)
    public ProfileRoleListResponse getRoles(String primaryRoleCode) {
        ProfileRoleCategory primaryRole = profileRoleCategoryRepository.findByCode(primaryRoleCode)
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 대표 역할입니다."));

        return new ProfileRoleListResponse(
                primaryRole.getCode(),
                primaryRole.getName(),
                profileRoleRepository.findAllByPrimaryRole_CodeOrderByDisplayOrderAsc(primaryRoleCode).stream()
                        .map(ProfileRoleResponse::from)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public ProfileTagListResponse getTags(ProfileTag.TagType type) {
        return new ProfileTagListResponse(
                type,
                profileTagRepository.findAllByTagTypeOrderByIdAsc(type).stream()
                        .map(ProfileTagResponse::from)
                        .toList()
        );
    }
}
