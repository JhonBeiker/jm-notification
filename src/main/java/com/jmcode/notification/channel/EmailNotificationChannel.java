package com.jmcode.notification.channel;

import com.jmcode.notification.domain.ChannelType;
import com.jmcode.notification.domain.NotificationRequest;
import com.jmcode.notification.domain.NotificationResult;
import com.jmcode.notification.email.EmailAccount;
import com.jmcode.notification.email.EmailAccountManager;
import com.jmcode.notification.email.EmailAccountService;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class EmailNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    private final EmailAccountService accountService;
    private final EmailAccountManager accountManager;

    public EmailNotificationChannel(EmailAccountService accountService, EmailAccountManager accountManager) {
        this.accountService = accountService;
        this.accountManager = accountManager;
    }

    @Override
    public ChannelType supports() {
        return ChannelType.EMAIL;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public NotificationResult send(NotificationRequest request) {
        try {
            String clientCode = resolveClientCode(request);
            EmailAccount account = accountService.resolveAccount(clientCode);
            JavaMailSender mailSender = accountManager.getSender(account);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String fromEmail = StringUtils.hasText(account.getFromAddress()) ? account.getFromAddress() : account.getUsername();
            String fromName = account.getFromName();

            if (StringUtils.hasText(fromName)) {
                helper.setFrom(new InternetAddress(fromEmail, fromName));
            } else {
                helper.setFrom(fromEmail);
            }

            if (StringUtils.hasText(account.getReplyTo())) {
                helper.setReplyTo(account.getReplyTo());
            }

            helper.setTo(request.to());
            helper.setSubject(StringUtils.hasText(request.subject()) ? request.subject() : "Notification");

            boolean isHtml = request.message() != null && (request.message().contains("<html>") || request.message().contains("<p>"));
            helper.setText(request.message(), isHtml);

            mailSender.send(mimeMessage);
            log.info("Email sent to {} using clientCode={}", request.to(), account.getClientCode());
            return NotificationResult.sent(supports(), request.to(), "email-" + account.getClientCode() + "-" + System.currentTimeMillis());
        } catch (Exception ex) {
            log.error("Failed to send email to {}: {}", request.to(), ex.getMessage());
            return NotificationResult.failed(supports(), request.to(), ex.getMessage());
        }
    }

    private static String resolveClientCode(NotificationRequest request) {
        if (StringUtils.hasText(request.clientCode())) {
            return request.clientCode();
        }
        if (request.metadata() != null && request.metadata().containsKey("clientCode")) {
            return request.metadata().get("clientCode");
        }
        return null;
    }
}
