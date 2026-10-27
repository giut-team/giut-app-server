package com.giut.server.user.service;

import com.giut.server.global.exception.TooManyRequestsException;
import com.giut.server.user.dto.request.UniversityEmailSendRequest;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UniversityEmailVerificationServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final MailService mail = mock(MailService.class);
    private final UniversityEmailVerificationService service = new UniversityEmailVerificationService(
            users, encoder, mail, new UniversityEmailSendRateLimiter());

    @Test
    void changedEmailsShareUserQuotaAndRejectionDoesNotHashOrSend() {
        when(encoder.encode(anyString())).thenReturn("hash");
        for (int i = 0; i < 5; i++) service.sendCode(1L, request("target" + i + "@uos.ac.kr"));
        assertThatThrownBy(() -> service.sendCode(1L, request("another@uos.ac.kr")))
                .isInstanceOf(TooManyRequestsException.class);
        verify(encoder, times(5)).encode(anyString());
        verify(mail, times(5)).sendUniversityVerificationCode(anyString(), anyString());
    }

    @Test
    void rejectedResendPreservesExistingCodeAndExpiry() {
        when(encoder.encode(anyString())).thenReturn("hash");
        var request = request("target@uos.ac.kr");
        for (int i = 0; i < 5; i++) service.sendCode(1L, request);
        var codes = (java.util.Map<?, ?>) ReflectionTestUtils.getField(service, "verificationCodes");
        Object saved = codes.get("1:target@uos.ac.kr");
        assertThatThrownBy(() -> service.sendCode(1L, request)).isInstanceOf(TooManyRequestsException.class);
        assertThat(codes.get("1:target@uos.ac.kr")).isSameAs(saved);
    }

    @Test
    void failedMailAttemptsAlsoConsumeQuota() {
        doThrow(new IllegalStateException("synthetic mail failure"))
                .when(mail).sendUniversityVerificationCode(anyString(), anyString());
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.sendCode(1L, request("target@uos.ac.kr")))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> service.sendCode(1L, request("target@uos.ac.kr")))
                .isInstanceOf(TooManyRequestsException.class);
        verify(mail, times(5)).sendUniversityVerificationCode(anyString(), anyString());
    }

    @Test
    void invalidAndAlreadyUsedEmailsDoNotConsumeQuota() {
        when(users.existsByUniversityEmail("used@uos.ac.kr")).thenReturn(true);
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> service.sendCode(1L, request("invalid@example.com")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.sendCode(1L, request("used@uos.ac.kr")))
                    .isInstanceOf(com.giut.server.global.exception.ConflictException.class);
        }
        for (int i = 0; i < 5; i++) service.sendCode(1L, request("valid@uos.ac.kr"));
        verify(mail, times(5)).sendUniversityVerificationCode(anyString(), anyString());
    }

    private UniversityEmailSendRequest request(String email) {
        var request = new UniversityEmailSendRequest();
        ReflectionTestUtils.setField(request, "universityEmail", email);
        return request;
    }
}
