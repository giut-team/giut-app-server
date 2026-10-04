package com.giut.server.profile.service;

import com.giut.server.profile.dto.request.UpdateMyProfileRequest;
import com.giut.server.profile.entity.ProfileRole;
import com.giut.server.user.entity.User;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.repository.ActivityHistoryRepository;
import com.giut.server.competition.repository.CompetitionScrapRepository;
import com.giut.server.profile.repository.PortfolioItemRepository;
import com.giut.server.profile.repository.PortfolioItemRoleRepository;
import com.giut.server.profile.repository.ProfileRoleRepository;
import com.giut.server.profile.repository.ProfileRoleSkillTagRepository;
import com.giut.server.profile.repository.ProfileTagRepository;
import com.giut.server.team.repository.TeamMemberRepository;
import com.giut.server.team.repository.TeamScrapRepository;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.profile.repository.UserProfileRoleRepository;
import com.giut.server.profile.repository.UserProfileTagRepository;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileUpdateServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private ProfileRoleRepository profileRoleRepository;
    @Mock private UserProfileRoleRepository userProfileRoleRepository;
    @Mock private ProfileTagRepository profileTagRepository;
    @Mock private UserProfileTagRepository userProfileTagRepository;
    @Mock private ProfileRoleSkillTagRepository profileRoleSkillTagRepository;
    @Mock private PortfolioItemRepository portfolioItemRepository;
    @Mock private PortfolioItemRoleRepository portfolioItemRoleRepository;
    @Mock private ActivityHistoryRepository activityHistoryRepository;
    @Mock private ActivityHistoryService activityHistoryService;
    @Mock private CompetitionScrapRepository competitionScrapRepository;
    @Mock private TeamScrapRepository teamScrapRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @InjectMocks private UserProfileService userProfileService;

    @Test
    void updateKeepsNicknameAndGenderWhileReplacingActivityHistories() {
        User user = User.createOAuthUser("member@example.com", "기존 이름", User.OAuthProvider.KAKAO, "kakao-12");
        ReflectionTestUtils.setField(user, "id", 12L);
        UserProfile profile = UserProfile.create(
                user, UserProfile.DepartmentType.DESIGN,
                UserProfile.ActivityStatus.LOOKING_FOR_TEAM, (short) 2,
                UserProfile.Gender.MALE, null, null, true, "[\"DEVELOPMENT\"]"
        );
        ReflectionTestUtils.setField(profile, "userId", 12L);
        ProfileRole role = mock(ProfileRole.class);
        when(role.getCode()).thenReturn("BACKEND_DEVELOPER");
        when(role.getPrimaryRole()).thenReturn(ProfileRole.PrimaryRole.DEVELOPMENT);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(userProfileRepository.findById(12L)).thenReturn(Optional.of(profile));
        when(profileRoleRepository.findAllByCodeIn(List.of("BACKEND_DEVELOPER"))).thenReturn(List.of(role));
        when(profileTagRepository.findAllById(any())).thenReturn(List.of());
        when(userProfileRoleRepository.findAllByProfile_UserId(12L)).thenReturn(List.of());
        when(userProfileTagRepository.findAllByProfile_UserId(12L)).thenReturn(List.of());
        when(profileRoleSkillTagRepository.findAllByTag_IdIn(List.of())).thenReturn(List.of());
        when(portfolioItemRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(12L)).thenReturn(List.of());
        when(activityHistoryRepository.findAllByUser_IdOrderByStartMonthDescEndMonthDescIdDesc(12L))
                .thenReturn(List.of());

        UpdateMyProfileRequest request = new UpdateMyProfileRequest(
                UserProfile.DepartmentType.COMPUTER_SCIENCE,
                List.of("DEVELOPMENT"),
                List.of("BACKEND_DEVELOPER"),
                UserProfile.ActivityStatus.LOOKING_FOR_TEAM,
                (short) 3,
                null,
                "새 소개",
                true,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        var response = userProfileService.updateMyProfile(12L, request);

        assertThat(user.getNickname()).isEqualTo("기존 이름");
        assertThat(profile.getGender()).isEqualTo(UserProfile.Gender.MALE);
        assertThat(profile.getDepartment()).isEqualTo(UserProfile.DepartmentType.COMPUTER_SCIENCE);
        assertThat(response.profile().nickname()).isEqualTo("기존 이름");
        assertThat(response.profile().departmentName()).isEqualTo("컴퓨터과학부");
        verify(activityHistoryService).replaceForProfile(user, List.of());
    }
}
