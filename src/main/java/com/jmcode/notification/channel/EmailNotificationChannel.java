package com.jmcode.notification.channel;

import com.jmcode.notification.domain.ChannelType;
import com.jmcode.notification.domain.NotificationRequest;
import com.jmcode.notification.domain.NotificationResult;
import com.jmcode.notification.email.EmailAccount;
import com.jmcode.notification.email.EmailAccountManager;
import com.jmcode.notification.email.EmailAccountService;
import com.jmcode.notification.email.EmailTemplate;
import com.jmcode.notification.email.EmailTemplateRepository;
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
    private final EmailTemplateRepository templateRepository;

    public EmailNotificationChannel(EmailAccountService accountService, EmailAccountManager accountManager, EmailTemplateRepository templateRepository) {
        this.accountService = accountService;
        this.accountManager = accountManager;
        this.templateRepository = templateRepository;
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

            // Obtener template si se especifica
            String finalSubject = request.subject();
            String finalMessage = request.message();
            boolean isHtml = false;

            if (request.templateName() != null) {
                EmailTemplate template = templateRepository.findByTenantIdAndName(account.getTenantId(), request.templateName())
                        .orElseThrow(() -> new RuntimeException("Template not found: " + request.templateName()));

                if (template.isActive()) {
                    finalSubject = renderTemplate(template.getSubject(), request.variables());
                    finalMessage = renderTemplate(template.getContent(), request.variables());
                    isHtml = "html".equalsIgnoreCase(template.getContentType());
                }
            } else {
                isHtml = isHtmlContent(finalMessage);
            }

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
            helper.setSubject(finalSubject);

            helper.setText(finalMessage, isHtml);

            mailSender.send(mimeMessage);
            log.info("Email sent to {} using clientCode={} template={}", request.to(), account.getClientCode(), request.templateName());
            return NotificationResult.sent(supports(), request.to(), "email-" + account.getClientCode() + "-" + System.currentTimeMillis());
        } catch (Exception ex) {
            log.error("Failed to send email to {}: {}", request.to(), ex.getMessage());
            return NotificationResult.failed(supports(), request.to(), ex.getMessage());
        }
    }

    private String renderTemplate(String template, java.util.Map<String, String> variables) {
        if (template == null || variables == null || variables.isEmpty()) {
            return template;
        }
        String result = template;
        for (java.util.Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            result = result.replace(placeholder, entry.getValue());
        }
        return result;
    }

    private boolean isHtmlContent(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        String lower = content.toLowerCase();
        return lower.contains("<html") || lower.contains("<p>") || lower.contains("<h1>")
                || lower.contains("<h2>") || lower.contains("<h3>") || lower.contains("<div>")
                || lower.contains("<span>") || lower.contains("<table>") || lower.contains("<br>")
                || lower.contains("<b>") || lower.contains("<i>") || lower.contains("<u>")
                || lower.contains("<strong>") || lower.contains("<em>") || lower.contains("<a ")
                || lower.contains("<img") || lower.contains("<ul>") || lower.contains("<ol>")
                || lower.contains("<li>") || lower.contains("<!doctype");
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