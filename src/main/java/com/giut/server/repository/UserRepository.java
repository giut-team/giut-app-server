package com.giut.server.repository;

import com.giut.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    boolean existsByUniversityEmail(String universityEmail);

    Optional<User> findByEmail(String email);

    List<User> findAllByIdInAndStatus(Collection<Long> ids, User.Status status);
}
