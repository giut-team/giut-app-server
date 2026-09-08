package com.giut.server.dto.university.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class UniversityEmailVerifyResponse {

    private Long memberId;

    private String universityEmail;

    private LocalDateTime universityVerifiedAt;
}
