package com.giut.server.profile.service;

import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.profile.dto.request.UpdateDepartmentRequest;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserProfileServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @InjectMocks private AdminUserProfileService service;

    private final UpdateDepartmentRequest request =
            new UpdateDepartmentRequest(UserProfile.DepartmentType.COMPUTER_SCIENCE);

    @Test
    void activeAdminChangesOnlyTheDepartment() {
        UserProfile profile = UserProfile.create(
                User.createOAuthUser("student@example.com", "학생", User.OAuthProvider.KAKAO, "student"),
                UserProfile.DepartmentType.DESIGN, UserProfile.ActivityStatus.RESTING, (short) 2,
                UserProfile.Gender.MALE, "https://example.com/profile.png", "기존 소개", false,
                "[\"DESIGN\"]"
        );
        when(userRepository.existsByIdAndRoleAndStatus(1L, User.Role.ADMIN, User.Status.ACTIVE)).thenReturn(true);
        when(userProfileRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(profile));

        var response = service.updateDepartment(1L, 12L, request);

        assertThat(profile.getDepartment()).isEqualTo(UserProfile.DepartmentType.COMPUTER_SCIENCE);
        assertThat(response.name()).isEqualTo("컴퓨터과학부");
        assertThat(profile.getActivityStatus()).isEqualTo(UserProfile.ActivityStatus.RESTING);
        assertThat(profile.getGrade()).isEqualTo((short) 2);
        assertThat(profile.getGender()).isEqualTo(UserProfile.Gender.MALE);
        assertThat(profile.getProfileImageUrl()).isEqualTo("https://example.com/profile.png");
        assertThat(profile.getBio()).isEqualTo("기존 소개");
        assertThat(profile.isSearchable()).isFalse();
        assertThat(profile.getPrimaryRolesJson()).isEqualTo("[\"DESIGN\"]");
    }

    @Test
    void accountWithoutActiveAdminRoleCannotChangeDepartmentEvenIfServiceIsCalledDirectly() {
        when(userRepository.existsByIdAndRoleAndStatus(1L, User.Role.ADMIN, User.Status.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> service.updateDepartment(1L, 12L, request))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void missingProfileIsNotCreatedByDepartmentChange() {
        when(userRepository.existsByIdAndRoleAndStatus(1L, User.Role.ADMIN, User.Status.ACTIVE)).thenReturn(true);
        when(userProfileRepository.findByIdForUpdate(12L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateDepartment(1L, 12L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

}
