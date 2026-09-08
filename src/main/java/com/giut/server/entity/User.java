package com.giut.server.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

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
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private Role role;

    @Column(name = "university_email", unique = true, length = 255)
    private String universityEmail;

    @Column(name = "university_verified_at")
    private LocalDateTime universityVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private Status status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private UserProfile profile;

    public static User createAdmin(String email, String passwordHash, String nickname, String phone) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.nickname = nickname;
        user.phone = phone;
        user.role = Role.ADMIN;
        user.status = Status.ACTIVE;
        user.createdAt = LocalDateTime.now();
        user.updatedAt = user.createdAt;
        return user;
    }

    public void verifyUniversityEmail(String universityEmail) {
        this.universityEmail = universityEmail;
        this.universityVerifiedAt = LocalDateTime.now();
        this.updatedAt = this.universityVerifiedAt;
    }
}
