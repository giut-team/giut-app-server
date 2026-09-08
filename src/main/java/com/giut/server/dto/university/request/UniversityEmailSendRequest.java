package com.giut.server.dto.university.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UniversityEmailSendRequest {

    @Email(message = "올바른 학교 이메일 형식이 아닙니다.")
    @NotBlank(message = "학교 이메일은 필수 항목입니다.")
    @Pattern(
            regexp = "^[A-Za-z0-9._%+-]+@uos\\.ac\\.kr$",
            message = "서울시립대 이메일(@uos.ac.kr)만 사용할 수 있습니다."
    )
    private String universityEmail;
}
