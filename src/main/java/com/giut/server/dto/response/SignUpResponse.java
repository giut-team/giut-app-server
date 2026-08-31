package com.giut.server.dto.response;

import com.giut.server.entity.Member;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SignUpResponse {

    private long id;

    private String email;

    private String nickname;

    private Member.Role role;

}
