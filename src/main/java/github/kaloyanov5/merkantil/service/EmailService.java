package github.kaloyanov5.merkantil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public void sendVerificationEmail(String to, String token) {
        String verifyLink = frontendUrl + "/verify-email?token=" + token;

        String html = """
                <html><body style="font-family:Arial,sans-serif;background:#f4f4f4;padding:20px">
                  <div style="max-width:480px;margin:auto;background:#fff;border-radius:8px;padding:32px">
                    <h2 style="color:#1a1a1a">Verify your Merkantil account</h2>
                    <p style="color:#555">Click the button below to verify your email address. This link expires in 24 hours.</p>
                    <a href="%s"
                       style="display:inline-block;margin-top:16px;padding:12px 24px;background:#0066ff;color:#fff;
                              text-decoration:none;border-radius:6px;font-weight:bold">
                      Verify Email
                    </a>
                    <p style="margin-top:24px;color:#999;font-size:12px">
                      If you did not create a Merkantil account, you can ignore this email.
                    </p>
                  </div>
                </body></html>
                """.formatted(verifyLink);

        sendHtmlEmail(to, "Verify your Merkantil account", html);
    }

    public void send2faEmail(String to, String code) {
        String html = """
                <html><body style="font-family:Arial,sans-serif;background:#f4f4f4;padding:20px">
                  <div style="max-width:480px;margin:auto;background:#fff;border-radius:8px;padding:32px">
                    <h2 style="color:#1a1a1a">Your login code</h2>
                    <p style="color:#555">Use the code below to complete your Merkantil login. It expires in 5 minutes.</p>
                    <div style="margin:24px 0;text-align:center;font-size:36px;font-weight:bold;
                                letter-spacing:8px;color:#0066ff;background:#f0f4ff;
                                padding:16px;border-radius:8px">
                      %s
                    </div>
                    <p style="color:#555">If you did not attempt to log in, secure your account immediately.</p>
                  </div>
                </body></html>
                """.formatted(code);

        sendHtmlEmail(to, "Your Merkantil login code", html);
    }

    public void sendPasswordResetEmail(String to, String code) {
        String html = """
                <html><body style="font-family:Arial,sans-serif;background:#f4f4f4;padding:20px">
                  <div style="max-width:480px;margin:auto;background:#fff;border-radius:8px;padding:32px">
                    <h2 style="color:#1a1a1a">Reset your password</h2>
                    <p style="color:#555">Use the code below to reset your Merkantil password. It expires in 15 minutes.</p>
                    <div style="margin:24px 0;text-align:center;font-size:36px;font-weight:bold;
                                letter-spacing:8px;color:#0066ff;background:#f0f4ff;
                                padding:16px;border-radius:8px">
                      %s
                    </div>
                    <p style="color:#555">Enter this code on the password reset page along with your new password.</p>
                    <p style="margin-top:24px;color:#999;font-size:12px">
                      If you did not request a password reset, you can ignore this email.
                    </p>
                  </div>
                </body></html>
                """.formatted(code);

        sendHtmlEmail(to, "Reset your Merkantil password", html);
    }

    private void sendHtmlEmail(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, "Merkantil");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email sent to {}: {}", to, subject);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
