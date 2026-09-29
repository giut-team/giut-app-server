package com.giut.server.dto.profile.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giut.server.entity.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SharedProfileResponseTest {

    @Test
    void serializedGuestResponseDoesNotExposePortfolioOrActivityHistory() throws Exception {
        SharedProfileResponse response = new SharedProfileResponse(
                "회원", true, null, UserProfile.ActivityStatus.LOOKING_FOR_TEAM,
                "팀 찾는 중", List.of(), "컴퓨터과학부", (short) 3, "소개", List.of()
        );

        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json).contains("nickname", "skills")
                .doesNotContain("portfolioItems", "activityHistories", "userId");
    }
}
