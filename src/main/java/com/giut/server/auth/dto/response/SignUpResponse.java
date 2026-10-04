package com.giut.server.auth.dto.response;

import com.giut.server.user.entity.User;
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
