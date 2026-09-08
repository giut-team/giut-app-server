package com.giut.server.service;

import com.giut.server.dto.response.DepartmentListResponse;
import com.giut.server.dto.response.DepartmentResponse;
import com.giut.server.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    public DepartmentListResponse getDepartments() {
        return new DepartmentListResponse(
                Arrays.stream(UserProfile.DepartmentType.values())
                        .map(DepartmentResponse::from)
                        .toList()
        );
    }
}
