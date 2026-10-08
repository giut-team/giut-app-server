package com.giut.server.profile.service;

import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.profile.entity.ProfileRecommendation;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.repository.ProfileRecommendationRepository;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileRecommendationServiceTest {
    @Mock private ProfileRecommendationRepository recommendationRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private ProfileRecommendationService service;

    @Test
    void recommendsPublicProfileAndReturnsReceivedCount() {
        User actor = user(1L);
        UserProfile target = profile(2L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(actor));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(target.getUser()));
        when(userProfileRepository.findById(2L)).thenReturn(Optional.of(target));
        when(recommendationRepository.existsByRecommender_IdAndRecommendedUser_Id(1L, 2L))
                .thenReturn(false, true);
        when(recommendationRepository.countByRecommendedUser_Id(2L)).thenReturn(5L);

        var response = service.recommend(1L, 2L);

        var saved = ArgumentCaptor.forClass(ProfileRecommendation.class);
        verify(recommendationRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRecommender()).isSameAs(actor);
        assertThat(saved.getValue().getRecommendedUser()).isSameAs(target.getUser());
        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.recommended()).isTrue();
        assertThat(response.recommendationCount()).isEqualTo(5L);
        var order = inOrder(userRepository, recommendationRepository);
        order.verify(userRepository).findByIdForUpdate(1L);
        order.verify(userRepository).findByIdForUpdate(2L);
        order.verify(recommendationRepository).existsByRecommender_IdAndRecommendedUser_Id(1L, 2L);
        order.verify(recommendationRepository).saveAndFlush(any());
    }

    @Test
    void duplicateRecommendationIsIdempotent() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(user(2L)));
        when(userProfileRepository.findById(2L)).thenReturn(Optional.of(profile(2L)));
        when(recommendationRepository.existsByRecommender_IdAndRecommendedUser_Id(1L, 2L)).thenReturn(true);
        when(recommendationRepository.countByRecommendedUser_Id(2L)).thenReturn(5L);

        assertThat(service.recommend(1L, 2L).recommendationCount()).isEqualTo(5L);
        verify(recommendationRepository, never()).saveAndFlush(any());
    }

    @Test
    void cannotRecommendSelf() {
        assertThatThrownBy(() -> service.recommend(1L, 1L)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(userRepository, userProfileRepository, recommendationRepository);
    }

    @Test
    void locksUsersInAscendingOrderWhenActorHasLargerId() {
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(user(2L)));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(profile(1L)));
        when(recommendationRepository.existsByRecommender_IdAndRecommendedUser_Id(2L, 1L)).thenReturn(true);

        service.recommend(2L, 1L);

        var order = inOrder(userRepository);
        order.verify(userRepository).findByIdForUpdate(1L);
        order.verify(userRepository).findByIdForUpdate(2L);
        verify(recommendationRepository, never()).saveAndFlush(any());
    }

    @Test
    void inactiveActorCannotRecommend() {
        User actor = user(1L);
        ReflectionTestUtils.setField(actor, "status", User.Status.DELETED);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service.recommend(1L, 2L)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(recommendationRepository, userProfileRepository);
    }

    @Test
    void cannotRecommendHiddenProfile() {
        UserProfile target = profile(2L);
        ReflectionTestUtils.setField(target, "searchable", false);
        assertRejectedTarget(target);
    }

    @Test
    void cannotRecommendRestingProfile() {
        UserProfile target = profile(2L);
        ReflectionTestUtils.setField(target, "activityStatus", UserProfile.ActivityStatus.RESTING);
        assertRejectedTarget(target);
    }

    @Test
    void cannotRecommendInactiveTarget() {
        User target = user(2L);
        ReflectionTestUtils.setField(target, "status", User.Status.DELETED);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        assertThatThrownBy(() -> service.recommend(1L, 2L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(recommendationRepository, userProfileRepository);
    }

    @Test
    void cannotRecommendMissingProfile() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(user(2L)));

        assertThatThrownBy(() -> service.recommend(1L, 2L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(recommendationRepository);
    }

    @Test
    void cancelOnlyDeletesActorsRecommendationEvenIfTargetIsNowHidden() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(recommendationRepository.countByRecommendedUser_Id(2L)).thenReturn(4L);

        var response = service.cancelRecommendation(1L, 2L);

        assertThat(response.recommended()).isFalse();
        assertThat(response.recommendationCount()).isEqualTo(4L);
        verify(recommendationRepository).deleteByRecommender_IdAndRecommendedUser_Id(1L, 2L);
        verify(recommendationRepository).flush();
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void repeatedCancellationReturnsUnrecommendedWithoutError() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));

        assertThat(service.cancelRecommendation(1L, 2L).recommended()).isFalse();
        assertThat(service.cancelRecommendation(1L, 2L).recommended()).isFalse();
        verify(recommendationRepository, times(2)).deleteByRecommender_IdAndRecommendedUser_Id(1L, 2L);
    }

    @Test
    void readsRecommendationStatusWithoutWrites() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(userProfileRepository.findById(2L)).thenReturn(Optional.of(profile(2L)));
        when(recommendationRepository.existsByRecommender_IdAndRecommendedUser_Id(1L, 2L)).thenReturn(true);
        when(recommendationRepository.countByRecommendedUser_Id(2L)).thenReturn(9L);

        var response = service.getRecommendation(1L, 2L);

        assertThat(response.recommended()).isTrue();
        assertThat(response.recommendationCount()).isEqualTo(9L);
        verify(recommendationRepository, never()).saveAndFlush(any());
        verify(userRepository, never()).findByIdForUpdate(any());
    }

    private void assertRejectedTarget(UserProfile target) {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(target.getUser()));
        when(userProfileRepository.findById(2L)).thenReturn(Optional.of(target));
        assertThatThrownBy(() -> service.recommend(1L, 2L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(recommendationRepository);
    }

    private User user(Long id) {
        User user = User.createOAuthUser("member" + id + "@example.com", "회원" + id,
                User.OAuthProvider.KAKAO, "kakao-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UserProfile profile(Long id) {
        UserProfile profile = UserProfile.create(user(id), UserProfile.DepartmentType.COMPUTER_SCIENCE,
                UserProfile.ActivityStatus.LOOKING_FOR_TEAM, (short) 3, UserProfile.Gender.MALE,
                null, "소개", true, "[\"DEVELOPMENT\"]");
        ReflectionTestUtils.setField(profile, "userId", id);
        return profile;
    }
}
