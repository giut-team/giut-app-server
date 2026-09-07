package com.giut.server.repository;

import com.giut.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    boolean existsByUniversityEmail(String universityEmail);

    Optional<User> findByEmail(String email);
}
