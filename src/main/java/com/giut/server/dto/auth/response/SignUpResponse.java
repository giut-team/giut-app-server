package com.giut.server.dto.auth.response;

import com.giut.server.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SignUpResponse {

    private long id;

    private String email;

    private String nickname;

    private User.Role role;

}
