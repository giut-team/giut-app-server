package com.giut.server.repository;

import com.giut.server.entity.User;
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

    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    Optional<User> findByOauthProviderAndOauthProviderId(
            User.OAuthProvider oauthProvider,
            String oauthProviderId
    );

    List<User> findAllByIdInAndStatus(Collection<Long> ids, User.Status status);
}
