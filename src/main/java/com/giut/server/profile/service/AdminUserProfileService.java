package com.giut.server.profile.service;

import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.profile.dto.request.UpdateDepartmentRequest;
import com.giut.server.profile.dto.response.DepartmentResponse;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional
    public DepartmentResponse updateDepartment(Long adminUserId, Long userId, UpdateDepartmentRequest request) {
        if (!userRepository.existsByIdAndRoleAndStatus(adminUserId, User.Role.ADMIN, User.Status.ACTIVE)) {
            throw new ForbiddenException("관리자 권한이 필요합니다.");
        }

        UserProfile profile = userProfileRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("변경할 프로필을 찾을 수 없습니다."));
        profile.changeDepartment(request.department());
        return DepartmentResponse.from(profile.getDepartment());
    }
}
