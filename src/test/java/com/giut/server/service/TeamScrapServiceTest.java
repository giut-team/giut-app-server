package com.giut.server.service;

import com.giut.server.entity.Team;
import com.giut.server.entity.TeamScrap;
import com.giut.server.entity.User;
import com.giut.server.repository.TeamRepository;
import com.giut.server.repository.TeamScrapRepository;
import com.giut.server.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamScrapServiceTest {

    @Mock private TeamScrapRepository teamScrapRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private TeamScrapService teamScrapService;

    @Test
    void savesTeamOnceAndReturnsScrappedState() {
        User user = User.createOAuthUser("member@example.com", "회원", User.OAuthProvider.KAKAO, "kakao-12");
        Team team = org.mockito.Mockito.mock(Team.class);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(teamRepository.findById(7L)).thenReturn(Optional.of(team));

        assertThat(teamScrapService.scrap(12L, 7L).scrapped()).isTrue();
        verify(teamScrapRepository).save(any(TeamScrap.class));
    }

    @Test
    void repeatedScrapDoesNotCreateDuplicate() {
        User user = User.createOAuthUser("member@example.com", "회원", User.OAuthProvider.KAKAO, "kakao-12");
        Team team = org.mockito.Mockito.mock(Team.class);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(teamRepository.findById(7L)).thenReturn(Optional.of(team));
        when(teamScrapRepository.existsByUser_IdAndTeam_Id(12L, 7L)).thenReturn(true);

        assertThat(teamScrapService.scrap(12L, 7L).scrapped()).isTrue();
        verify(teamScrapRepository, never()).save(any(TeamScrap.class));
    }
}
