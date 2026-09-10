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

    public enum OAuthProvider {
        KAKAO,
        APPLE
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

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider", length = 20)
    private OAuthProvider oauthProvider;

    @Column(name = "oauth_provider_id", unique = true, length = 100)
    private String oauthProviderId;

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

    public static User createAdmin(String email, String passwordHash, String nickname) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.nickname = nickname;
        user.role = Role.ADMIN;
        user.status = Status.ACTIVE;
        user.createdAt = LocalDateTime.now();
        user.updatedAt = user.createdAt;
        return user;
    }

    public static User createOAuthUser(
            String email,
            String nickname,
            OAuthProvider oauthProvider,
            String oauthProviderId
    ) {
        User user = new User();
        user.email = email;
        user.nickname = nickname;
        user.oauthProvider = oauthProvider;
        user.oauthProviderId = oauthProviderId;
        user.role = Role.STUDENT;
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
