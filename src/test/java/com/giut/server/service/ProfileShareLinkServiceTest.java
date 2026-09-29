package com.giut.server.service;

import com.giut.server.dto.profile.response.ProfileShareLinkResponse;
import com.giut.server.dto.profile.response.SharedProfileResponse;
import com.giut.server.entity.ProfileShareLink;
import com.giut.server.entity.User;
import com.giut.server.entity.UserProfile;
import com.giut.server.exception.ResourceNotFoundException;
import com.giut.server.repository.ProfileShareLinkRepository;
import com.giut.server.repository.UserProfileRepository;
import com.giut.server.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileShareLinkServiceTest {

    @Mock private ProfileShareLinkRepository profileShareLinkRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserProfileService userProfileService;
    @InjectMocks private ProfileShareLinkService profileShareLinkService;

    @Test
    void createLinkStoresOnlyTokenHashEvenWhenProfileIsNotSearchable() {
        User user = User.createOAuthUser("member@example.com", "회원", User.OAuthProvider.KAKAO, "kakao-12");
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(userProfileRepository.existsById(12L)).thenReturn(true);
        when(profileShareLinkRepository.save(any(ProfileShareLink.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Instant before = Instant.now();
        ProfileShareLinkResponse response = profileShareLinkService.createLink(12L);
        Instant after = Instant.now();

        String token = response.sharePath().substring("/share/profile/".length());
        ArgumentCaptor<ProfileShareLink> saved = ArgumentCaptor.forClass(ProfileShareLink.class);
        verify(profileShareLinkRepository).save(saved.capture());
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(saved.getValue().getTokenHash()).hasSize(64).isNotEqualTo(token);
        assertThat(response.expiresAt()).isBetween(before.plus(Duration.ofHours(3)), after.plus(Duration.ofHours(3)));
    }

    @Test
    void revokedLinkCannotReadSharedProfile() {
        User user = User.createOAuthUser("member@example.com", "회원", User.OAuthProvider.KAKAO, "kakao-12");
        ProfileShareLink link = ProfileShareLink.create(user, "hash", Instant.now().plusSeconds(3600));
        ReflectionTestUtils.setField(link, "createdAt", Instant.now());
        link.revoke(Instant.now());
        when(profileShareLinkRepository.findByTokenHash(anyString())).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> profileShareLinkService.getSharedProfile("A".repeat(43)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userProfileService, never()).getSharedProfile(any());
    }

    @Test
    void validLinkReturnsOnlyTheSharedProfileResponse() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(12L);
        ProfileShareLink link = ProfileShareLink.create(user, "hash", Instant.now().plusSeconds(2 * 3600));
        ReflectionTestUtils.setField(link, "createdAt", Instant.now().minusSeconds(3600));
        SharedProfileResponse expected = new SharedProfileResponse(
                "회원", false, null, UserProfile.ActivityStatus.LOOKING_FOR_TEAM,
                "팀 찾는 중", java.util.List.of(), "컴퓨터과학부", (short) 3, "소개", java.util.List.of()
        );
        when(profileShareLinkRepository.findByTokenHash(anyString())).thenReturn(Optional.of(link));
        when(userProfileService.getSharedProfile(12L)).thenReturn(expected);

        assertThat(profileShareLinkService.getSharedProfile("A".repeat(43))).isSameAs(expected);
    }

    @Test
    void expiredLinkCannotReadSharedProfile() {
        User user = User.createOAuthUser("member@example.com", "회원", User.OAuthProvider.KAKAO, "kakao-12");
        ProfileShareLink link = ProfileShareLink.create(user, "hash", Instant.now().minusSeconds(1));
        ReflectionTestUtils.setField(link, "createdAt", Instant.now().minusSeconds(3600));
        when(profileShareLinkRepository.findByTokenHash(anyString())).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> profileShareLinkService.getSharedProfile("A".repeat(43)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userProfileService, never()).getSharedProfile(any());
    }

    @Test
    void previouslyIssuedLongLivedLinkAlsoExpiresAfterThreeHours() {
        User user = User.createOAuthUser("member@example.com", "회원", User.OAuthProvider.KAKAO, "kakao-12");
        ProfileShareLink link = ProfileShareLink.create(user, "hash", Instant.now().plus(Duration.ofDays(30)));
        ReflectionTestUtils.setField(link, "createdAt", Instant.now().minus(Duration.ofHours(4)));
        when(profileShareLinkRepository.findByTokenHash(anyString())).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> profileShareLinkService.getSharedProfile("A".repeat(43)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userProfileService, never()).getSharedProfile(any());
    }
}
