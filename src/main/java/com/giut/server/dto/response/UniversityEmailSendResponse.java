package com.giut.server.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class UniversityEmailSendResponse {

    private String universityEmail;

    private LocalDateTime expiresAt;
}
