package com.giut.server.service;

import com.giut.server.dto.profile.response.ProfileShareLinkResponse;
import com.giut.server.dto.profile.response.SharedProfileResponse;
import com.giut.server.entity.ProfileShareLink;
import com.giut.server.entity.User;
import com.giut.server.entity.UserProfile;
import com.giut.server.exception.ResourceNotFoundException;
import com.giut.server.repository.ProfileShareLinkRepository;
import com.giut.server.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class ProfileShareLinkService {

    private static final Duration LINK_VALIDITY = Duration.ofHours(3);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ProfileShareLinkRepository profileShareLinkRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileService userProfileService;

    @Transactional
    public ProfileShareLinkResponse createLink(Long userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .filter(found -> found.getUser().getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("공유할 프로필을 찾을 수 없습니다."));

        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        ProfileShareLink link = profileShareLinkRepository.save(ProfileShareLink.create(
                profile,
                hashToken(token),
                Instant.now().plus(LINK_VALIDITY)
        ));

        return new ProfileShareLinkResponse(
                link.getId(),
                token,
                link.getCreatedAt(),
                link.getExpiresAt()
        );
    }

    /* 공유 링크 목록 조회는 사용하지 않아 비활성화합니다.
    @Transactional(readOnly = true)
    public ProfileShareLinkListResponse getActiveLinks(Long userId) {
        return new ProfileShareLinkListResponse(profileShareLinkRepository
                .findAllByProfile_UserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, Instant.now())
                .stream()
                .map(ProfileShareLinkSummaryResponse::from)
                .toList());
    }
    */

    @Transactional(readOnly = true)
    public SharedProfileResponse getSharedProfile(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new ResourceNotFoundException("공유 프로필을 찾을 수 없습니다.");
        }
        Instant now = Instant.now();
        ProfileShareLink link = profileShareLinkRepository.findByTokenHash(hashToken(token))
                .filter(found -> found.isActive(now))
                .filter(found -> found.getCreatedAt().plus(LINK_VALIDITY).isAfter(now))
                .orElseThrow(() -> new ResourceNotFoundException("공유 프로필을 찾을 수 없습니다."));
        return userProfileService.getSharedProfile(link.getProfile().getUserId());
    }

    @Transactional
    public void revokeLink(Long userId, Long linkId) {
        ProfileShareLink link = profileShareLinkRepository.findByIdAndProfile_UserId(linkId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("공유 링크를 찾을 수 없습니다."));
        link.revoke(Instant.now());
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("공유 링크 토큰을 처리할 수 없습니다.", exception);
        }
    }
}
