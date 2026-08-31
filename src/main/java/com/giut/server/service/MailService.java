package com.giut.server.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendUniversityVerificationCode(String to, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();

            message.setFrom(fromEmail);
            message.setRecipients(MimeMessage.RecipientType.TO, to);
            message.setSubject("[기웃] 서울시립대 이메일 인증코드", "UTF-8");
            message.setContent("""
                    <div style="font-family: Arial, sans-serif; padding: 24px;">
                        <h2>기웃 이메일 인증</h2>
                        <p>아래 인증번호를 5분 안에 입력해주세요.</p>
                        <div style="font-size: 32px; font-weight: 700; letter-spacing: 4px; margin: 24px 0;">
                            %s
                        </div>
                        <p style="color: #777;">본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다.</p>
                    </div>
                    """.formatted(code), "text/html; charset=UTF-8");

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("인증 메일 전송에 실패했습니다.", e);
        }
    }
}
