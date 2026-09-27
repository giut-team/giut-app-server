package com.giut.server.repository;

import com.giut.server.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByEmail(String email);

    boolean existsByUniversityEmail(String universityEmail);

    Optional<Member> findByEmail(String email);
}
