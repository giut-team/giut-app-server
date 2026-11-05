package com.giut.server.user.repository;

import com.giut.server.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    boolean existsByUniversityEmail(String universityEmail);

    boolean existsByIdAndUniversityVerifiedAtIsNotNull(Long id);

    boolean existsByIdAndRoleAndStatus(Long id, User.Role role, User.Status status);

    boolean existsByIdAndStatus(Long id, User.Status status);

    Optional<User> findByEmail(String email);

    // User 행을 사용자 범위 뮤텍스로 빌려 써서, 같은 사용자에 대한 동시 요청이 서로의
    // 조회-수정 결과를 덮어쓰지 않도록 직렬화한다(예: 포트폴리오 쇼케이스 순서 재배치).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    Optional<User> findByOauthProviderAndOauthProviderId(
            User.OAuthProvider oauthProvider,
            String oauthProviderId
    );

    List<User> findAllByIdInAndStatus(Collection<Long> ids, User.Status status);
}
