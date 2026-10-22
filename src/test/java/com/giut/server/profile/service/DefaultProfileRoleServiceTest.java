package com.giut.server.profile.service;

import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.profile.dto.response.SeedDefaultProfileRolesResponse;
import com.giut.server.profile.repository.ProfileRoleRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultProfileRoleServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileRoleRepository profileRoleRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DefaultProfileRoleService service;

    @Test
    void activeAdminUsesTheExistingDataUpsertWithoutExecutingSchemaDdl() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(User.Role.ADMIN, User.Status.ACTIVE)));
        when(profileRoleRepository.count()).thenReturn(18L);

        SeedDefaultProfileRolesResponse response = service.seedDefaults(10L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture());
        assertThat(sql.getValue())
                .startsWith("INSERT INTO profile_roles ")
                .contains("ON DUPLICATE KEY UPDATE", "BACKEND_DEVELOPER", "BRAND_MARKETER")
                .doesNotContain("CREATE TABLE", "DROP TABLE");
        assertThat(response.totalRoleCount()).isEqualTo(18L);
    }

    @Test
    void studentCannotWriteDefaultRoles() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(User.Role.STUDENT, User.Status.ACTIVE)));

        assertThatThrownBy(() -> service.seedDefaults(10L)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(jdbcTemplate, profileRoleRepository);
    }

    @Test
    void suspendedAdminCannotWriteDefaultRoles() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(User.Role.ADMIN, User.Status.SUSPENDED)));

        assertThatThrownBy(() -> service.seedDefaults(10L)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(jdbcTemplate, profileRoleRepository);
    }

    @Test
    void missingAccountCannotWriteDefaultRoles() {
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.seedDefaults(10L)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(jdbcTemplate, profileRoleRepository);
    }

    private User user(User.Role role, User.Status status) {
        User user = User.createAdmin("admin@example.com", "password-hash", "관리자");
        ReflectionTestUtils.setField(user, "role", role);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }
}
