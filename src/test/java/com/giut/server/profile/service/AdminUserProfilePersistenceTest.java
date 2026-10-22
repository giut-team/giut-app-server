package com.giut.server.profile.service;

import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.profile.dto.request.UpdateDepartmentRequest;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_context;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@Transactional
class AdminUserProfilePersistenceTest {

    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private AdminUserProfileService service;
    @Autowired private EntityManager entityManager;

    @Test
    void departmentChangeIsStoredWithoutChangingOtherProfileFields() {
        User admin = userRepository.saveAndFlush(User.createAdmin("dept-admin@example.com", "hash", "관리자"));
        User target = userRepository.saveAndFlush(User.createOAuthUser(
                "dept-target@example.com", "학생", User.OAuthProvider.KAKAO, "department-target"));
        UserProfile profile = userProfileRepository.saveAndFlush(UserProfile.create(
                target, UserProfile.DepartmentType.DESIGN, UserProfile.ActivityStatus.RESTING,
                (short) 2, UserProfile.Gender.MALE, null, "기존 소개", false, "[\"DESIGN\"]"));
        Long targetId = profile.getUserId();
        entityManager.clear();

        service.updateDepartment(admin.getId(), targetId,
                new UpdateDepartmentRequest(UserProfile.DepartmentType.COMPUTER_SCIENCE));
        entityManager.flush();
        entityManager.clear();

        UserProfile stored = userProfileRepository.findById(targetId).orElseThrow();
        assertThat(stored.getDepartment()).isEqualTo(UserProfile.DepartmentType.COMPUTER_SCIENCE);
        assertThat(stored.getGrade()).isEqualTo((short) 2);
        assertThat(stored.getActivityStatus()).isEqualTo(UserProfile.ActivityStatus.RESTING);
        assertThat(stored.getBio()).isEqualTo("기존 소개");
        assertThat(stored.isSearchable()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("unauthorizedAccounts")
    void currentDatabaseRoleAndStatusAreRequired(User.Role role, User.Status status) {
        User caller = User.createAdmin("dept-caller@example.com", "hash", "호출자");
        ReflectionTestUtils.setField(caller, "role", role);
        ReflectionTestUtils.setField(caller, "status", status);
        userRepository.saveAndFlush(caller);
        entityManager.clear();

        assertThatThrownBy(() -> service.updateDepartment(caller.getId(), 999L,
                new UpdateDepartmentRequest(UserProfile.DepartmentType.COMPUTER_SCIENCE)))
                .isInstanceOf(ForbiddenException.class);
    }

    static Stream<Arguments> unauthorizedAccounts() {
        return Stream.of(
                Arguments.of(User.Role.STUDENT, User.Status.ACTIVE),
                Arguments.of(User.Role.ADMIN, User.Status.SUSPENDED),
                Arguments.of(User.Role.ADMIN, User.Status.DELETED)
        );
    }
}
