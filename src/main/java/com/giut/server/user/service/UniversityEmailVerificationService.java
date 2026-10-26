package com.giut.server.user.service;

import com.giut.server.user.dto.request.UniversityEmailSendRequest;
import com.giut.server.user.dto.request.UniversityEmailVerifyRequest;
import com.giut.server.user.dto.response.UniversityEmailSendResponse;
import com.giut.server.user.dto.response.UniversityEmailVerifyResponse;
import com.giut.server.user.entity.User;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.global.exception.ConflictException;
import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class UniversityEmailVerificationService {

    private static final int CODE_BOUND = 1_000_000;
    private static final int CODE_EXPIRE_MINUTES = 5;

    private final Map<String, VerificationCode> verificationCodes = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    public UniversityEmailSendResponse sendCode(Long memberId, UniversityEmailSendRequest request) {
        String universityEmail = request.getUniversityEmail();
        validateUniversityEmail(universityEmail);
        validateNotUsedUniversityEmail(universityEmail);

        String code = generateCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(CODE_EXPIRE_MINUTES);

        verificationCodes.put(
                createKey(memberId, universityEmail),
                new VerificationCode(passwordEncoder.encode(code), expiresAt)
        );

        mailService.sendUniversityVerificationCode(universityEmail, code);

        return new UniversityEmailSendResponse(universityEmail, expiresAt);
    }

    public boolean isUniversityVerified(Long memberId) {
        return userRepository.existsByIdAndUniversityVerifiedAtIsNotNull(memberId);
    }

    @Transactional
    public UniversityEmailVerifyResponse verifyCode(Long memberId, UniversityEmailVerifyRequest request) {
        String universityEmail = request.getUniversityEmail();
        validateUniversityEmail(universityEmail);

        // Re-read the code only after acquiring the same user lock used by revocation.
        // A verification already in progress must finish before a later revocation.
        User user = userRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다."));
        validateNotUsedUniversityEmail(universityEmail);

        String key = createKey(memberId, universityEmail);
        VerificationCode savedCode = verificationCodes.get(key);

        if (savedCode == null) {
            throw new IllegalArgumentException("인증코드 요청 내역이 없습니다.");
        }

        if (LocalDateTime.now().isAfter(savedCode.expiresAt())) {
            verificationCodes.remove(key);
            throw new IllegalArgumentException("인증코드가 만료되었습니다.");
        }

        if (!passwordEncoder.matches(request.getCode(), savedCode.codeHash())) {
            throw new IllegalArgumentException("인증코드가 일치하지 않습니다.");
        }

        user.verifyUniversityEmail(universityEmail);
        verificationCodes.remove(key);

        return new UniversityEmailVerifyResponse(
                user.getId(),
                user.getUniversityEmail(),
                user.getUniversityVerifiedAt()
        );
    }

    @Transactional
    public void revokeUniversityVerification(Long adminUserId, Long userId) {
        if (!userRepository.existsByIdAndRoleAndStatus(adminUserId, User.Role.ADMIN, User.Status.ACTIVE)) {
            throw new ForbiddenException("관리자 권한이 필요합니다.");
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 ID는 양수여야 합니다.");
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("인증을 해제할 사용자를 찾을 수 없습니다."));
        user.revokeUniversityVerification();

        // Invalidate every pending email for this user, but not IDs with the same numeric prefix.
        // The current application stores these short-lived codes in this process only.
        String userKeyPrefix = userId + ":";
        verificationCodes.keySet().removeIf(key -> key.startsWith(userKeyPrefix));
    }

    private void validateUniversityEmail(String universityEmail) {
        if (universityEmail == null || !universityEmail.endsWith("@uos.ac.kr")) {
            throw new IllegalArgumentException("서울시립대 이메일(@uos.ac.kr)만 사용할 수 있습니다.");
        }
    }

    private void validateNotUsedUniversityEmail(String universityEmail) {
        if (userRepository.existsByUniversityEmail(universityEmail)) {
            throw new ConflictException("이미 인증에 사용된 학교 이메일입니다.");
        }
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(CODE_BOUND));
    }

    private String createKey(Long memberId, String universityEmail) {
        return memberId + ":" + universityEmail;
    }

    private record VerificationCode(String codeHash, LocalDateTime expiresAt) {
    }
}
