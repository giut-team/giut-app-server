package com.giut.server.service;

import com.giut.server.dto.response.ProfileRoleListResponse;
import com.giut.server.dto.response.ProfileRoleResponse;
import com.giut.server.dto.response.ProfileTagListResponse;
import com.giut.server.dto.response.ProfileTagResponse;
import com.giut.server.entity.ProfileRoleCategory;
import com.giut.server.entity.ProfileTag;
import com.giut.server.exception.ResourceNotFoundException;
import com.giut.server.repository.ProfileRoleCategoryRepository;
import com.giut.server.repository.ProfileRoleRepository;
import com.giut.server.repository.ProfileTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileOptionService {

    private final ProfileRoleCategoryRepository profileRoleCategoryRepository;

    private final ProfileRoleRepository profileRoleRepository;

    private final ProfileTagRepository profileTagRepository;

    @Transactional(readOnly = true)
    public ProfileRoleListResponse getRoles(String primaryRoleCode) {
        ProfileRoleCategory primaryRole = profileRoleCategoryRepository.findByCode(primaryRoleCode)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 대표 역할입니다."));

        List<ProfileRoleResponse> roles = profileRoleRepository
                .findAllByPrimaryRole_CodeOrderByDisplayOrderAsc(primaryRoleCode).stream()
                .map(ProfileRoleResponse::from)
                .toList();

        if (roles.isEmpty()) {
            throw new ResourceNotFoundException("등록된 세부 역할이 없습니다.");
        }

        return new ProfileRoleListResponse(
                primaryRole.getCode(),
                primaryRole.getName(),
                roles
        );
    }

    @Transactional(readOnly = true)
    public ProfileTagListResponse getTags(ProfileTag.TagType type) {
        List<ProfileTagResponse> tags = profileTagRepository.findAllByTagTypeOrderByIdAsc(type).stream()
                .map(ProfileTagResponse::from)
                .toList();

        if (tags.isEmpty()) {
            throw new ResourceNotFoundException("등록된 프로필 태그가 없습니다.");
        }

        return new ProfileTagListResponse(type, tags);
    }
}
