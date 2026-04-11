package github.kaloyanov5.merkantil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
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

    private static final String LOGO = "<div style=\"text-align:center;margin-bottom:24px\"><img src=\"cid:logo\" alt=\"Merkantil\" style=\"height:48px\"></div>";

    public void sendVerificationEmail(String to, String token) {
        String verifyLink = frontendUrl + "/verify-email?token=" + token;

        String html = """
                <html><body style="font-family:Arial,sans-serif;background:#f4f4f4;padding:20px">
                  <div style="max-width:480px;margin:auto;background:#fff;border-radius:8px;padding:32px">
                    %s
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
                """.formatted(LOGO, verifyLink);

        sendHtmlEmail(to, "Verify your Merkantil account", html);
    }

    public void send2faEmail(String to, String code) {
        String html = """
                <html><body style="font-family:Arial,sans-serif;background:#f4f4f4;padding:20px">
                  <div style="max-width:480px;margin:auto;background:#fff;border-radius:8px;padding:32px">
                    %s
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
                """.formatted(LOGO, code);

        sendHtmlEmail(to, "Your Merkantil login code", html);
    }

    public void sendPasswordResetEmail(String to, String code) {
        String html = """
                <html><body style="font-family:Arial,sans-serif;background:#f4f4f4;padding:20px">
                  <div style="max-width:480px;margin:auto;background:#fff;border-radius:8px;padding:32px">
                    %s
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
                """.formatted(LOGO, code);

        sendHtmlEmail(to, "Reset your Merkantil password", html);
    }

    public void sendLimitOrderFilledEmail(String to, String side, String symbol,
                                           int quantity, double executionPrice, double totalValue,
                                           double refund) {
        String sideLabel = side.equals("BUY") ? "Buy" : "Sell";
        String sideColor = side.equals("BUY") ? "#16a34a" : "#dc2626";
        String refundHtml = (side.equals("BUY") && refund > 0)
                ? "<p style=\"color:#16a34a;margin-top:8px\">Refund for price difference: <strong>$%s</strong></p>"
                  .formatted(String.format("%.2f", refund))
                : "";

        String html = """
                <html><body style="font-family:Arial,sans-serif;background:#f4f4f4;padding:20px">
                  <div style="max-width:480px;margin:auto;background:#fff;border-radius:8px;padding:32px">
                    %s
                    <h2 style="color:#1a1a1a">Limit Order Filled</h2>
                    <p style="color:#555">Your limit order has been executed successfully.</p>
                    <div style="margin:24px 0;background:#f9fafb;border-radius:8px;padding:20px">
                      <table style="width:100%%;border-collapse:collapse">
                        <tr><td style="color:#888;padding:6px 0">Type</td>
                            <td style="text-align:right;font-weight:bold;color:%s">%s</td></tr>
                        <tr><td style="color:#888;padding:6px 0">Symbol</td>
                            <td style="text-align:right;font-weight:bold;color:#1a1a1a">%s</td></tr>
                        <tr><td style="color:#888;padding:6px 0">Quantity</td>
                            <td style="text-align:right;font-weight:bold;color:#1a1a1a">%d shares</td></tr>
                        <tr><td style="color:#888;padding:6px 0">Execution price</td>
                            <td style="text-align:right;font-weight:bold;color:#1a1a1a">$%s</td></tr>
                        <tr style="border-top:1px solid #e5e7eb">
                            <td style="color:#888;padding:10px 0 6px">Total</td>
                            <td style="text-align:right;font-weight:bold;font-size:18px;color:#1a1a1a;padding-top:10px">$%s</td></tr>
                      </table>
                      %s
                    </div>
                    <p style="color:#999;font-size:12px;margin-top:8px">
                      Log in to Merkantil to view your updated portfolio.
                    </p>
                  </div>
                </body></html>
                """.formatted(
                        LOGO, sideColor, sideLabel, symbol, quantity,
                        String.format("%.2f", executionPrice),
                        String.format("%.2f", totalValue),
                        refundHtml);

        sendHtmlEmail(to, "Limit order filled — " + sideLabel + " " + quantity + " " + symbol, html);
    }

    public void sendTransferReceivedEmail(String to, String senderEmail,
                                          java.math.BigDecimal amount, String description) {
        String descriptionHtml = (description != null && !description.isBlank())
                ? "<p style=\"color:#555;margin-top:8px\">Note: <em>%s</em></p>".formatted(description)
                : "";

        String html = """
                <html><body style="font-family:Arial,sans-serif;background:#f4f4f4;padding:20px">
                  <div style="max-width:480px;margin:auto;background:#fff;border-radius:8px;padding:32px">
                    %s
                    <h2 style="color:#1a1a1a">You received a transfer</h2>
                    <p style="color:#555">A transfer has been sent to your Merkantil wallet.</p>
                    <div style="margin:24px 0;background:#f9fafb;border-radius:8px;padding:20px">
                      <table style="width:100%%;border-collapse:collapse">
                        <tr><td style="color:#888;padding:6px 0">From</td>
                            <td style="text-align:right;font-weight:bold;color:#1a1a1a">%s</td></tr>
                        <tr style="border-top:1px solid #e5e7eb">
                            <td style="color:#888;padding:10px 0 6px">Amount</td>
                            <td style="text-align:right;font-weight:bold;font-size:18px;color:#16a34a;padding-top:10px">+$%s</td></tr>
                      </table>
                      %s
                    </div>
                    <p style="color:#999;font-size:12px;margin-top:8px">
                      Log in to Merkantil to view your updated balance.
                    </p>
                  </div>
                </body></html>
                """.formatted(LOGO, senderEmail, String.format("%.2f", amount), descriptionHtml);

        sendHtmlEmail(to, "You received $" + String.format("%.2f", amount) + " on Merkantil", html);
    }

    private void sendHtmlEmail(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, "Merkantil");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            helper.addInline("logo", new ClassPathResource("static/logo-big-blue.png"));
            mailSender.send(message);
            log.info("Email sent to {}: {}", to, subject);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
