package com.giut.server.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    public enum Role {
        STUDENT,
        ADMIN
    }

    public enum Status {
        ACTIVE,
        SUSPENDED,
        DELETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "student_no", unique = true, length = 30)
    private String studentNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "university_email", unique = true, length = 255)
    private String universityEmail;

    @Column(name = "university_verified_at")
    private LocalDateTime universityVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Member createAdmin(String email, String passwordHash, String nickname, String phone) {
        Member member = new Member();
        member.email = email;
        member.passwordHash = passwordHash;
        member.nickname = nickname;
        member.phone = phone;
        member.role = Role.ADMIN;
        member.status = Status.ACTIVE;
        member.createdAt = LocalDateTime.now();
        member.updatedAt = member.createdAt;
        return member;
    }
}
