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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UniversityEmailVerificationService {

    private final UserRepository userRepository;
    private final MailService mailService;
    private final UniversityEmailVerificationCodeStore codeStore;

    public UniversityEmailSendResponse sendCode(Long memberId, UniversityEmailSendRequest request) {
        String universityEmail = request.getUniversityEmail();
        validateUniversityEmail(universityEmail);
        validateNotUsedUniversityEmail(universityEmail);

        var issued = codeStore.issue(memberId, universityEmail);
        mailService.sendUniversityVerificationCode(universityEmail, issued.code());
        return new UniversityEmailSendResponse(universityEmail, issued.expiresAt());
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

        codeStore.verify(memberId, universityEmail, request.getCode());
        user.verifyUniversityEmail(universityEmail);

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

        codeStore.invalidateCodes(userId);
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

}
