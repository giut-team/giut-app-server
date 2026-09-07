package com.giut.server.service;

import com.giut.server.dto.request.PutMyProfileRequest;
import com.giut.server.dto.response.MyProfileResponse;
import com.giut.server.entity.Department;
import com.giut.server.entity.UserProfile;
import com.giut.server.exception.ResourceNotFoundException;
import com.giut.server.repository.DepartmentRepository;
import com.giut.server.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    private final DepartmentRepository departmentRepository;

    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(Long userId) {
        return userProfileRepository.findById(userId)
                .map(MyProfileResponse::from)
                .orElseGet(MyProfileResponse::notCompleted);
    }

    @Transactional
    public MyProfileSaveResult saveMyProfile(Long userId, PutMyProfileRequest request) {
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 학과입니다."));

        return userProfileRepository.findById(userId)
                .map(profile -> {
                    profile.update(
                            department,
                            request.grade(),
                            request.gender(),
                            request.profileImageUrl(),
                            request.bio(),
                            request.searchable()
                    );
                    return new MyProfileSaveResult(MyProfileResponse.from(profile), false);
                })
                .orElseGet(() -> {
                    UserProfile profile = UserProfile.create(
                            userId,
                            department,
                            request.grade(),
                            request.gender(),
                            request.profileImageUrl(),
                            request.bio(),
                            request.searchable()
                    );
                    UserProfile savedProfile = userProfileRepository.save(profile);
                    return new MyProfileSaveResult(MyProfileResponse.from(savedProfile), true);
                });
    }
}
