package com.jmcode.notification.email;

import com.jmcode.notification.channel.Attachment;
import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationChannel;
import com.jmcode.notification.channel.NotificationRequest;
import com.jmcode.notification.channel.NotificationResult;
import com.jmcode.notification.config.NotificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class EmailNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final EmailAccountService accountService;
    private final EmailAccountManager accountManager;
    private final EmailTemplateService templateService;
    private final EmailTemplateRenderer templateRenderer;
    private final NotificationProperties properties;

    @Override
    public ChannelType supports() {
        return ChannelType.EMAIL;
    }

    @Override
    public boolean isEnabled() {
        return properties.email().enabled();
    }

    @Override
    public NotificationResult send(NotificationRequest request) {
        if (!isEnabled()) {
            return NotificationResult.skipped(supports(), request.to(), "Email channel is disabled");
        }

        EmailAccount account;
        try {
            account = accountService.resolveAccount(request.effectiveClientCode());
        } catch (AccessDeniedException ex) {
            // Un clientCode de otra empresa es un 403, no un fallo del proveedor: se propaga.
            throw ex;
        } catch (RuntimeException ex) {
            return NotificationResult.failed(supports(), request.to(), ex.getMessage());
        }

        try {
            JavaMailSender mailSender = accountManager.getSender(account);
            RenderedEmail email = render(request, account);
            MimeMessage mimeMessage = buildMessage(mailSender, request, account, email);
            mailSender.send(mimeMessage);

            log.info("Email sent to {} clientCode={} template={} attachments={}",
                    request.to(), account.getClientCode(), request.templateName(), request.attachments().size());
            return NotificationResult.sent(supports(), request.to(), providerMessageId(mimeMessage, account));
        } catch (Exception ex) {
            log.error("Failed to send email to {} clientCode={}", request.to(), account.getClientCode(), ex);
            return NotificationResult.failed(supports(), request.to(), ex.getMessage());
        }
    }

    private RenderedEmail render(NotificationRequest request, EmailAccount account) {
        if (!request.hasTemplate()) {
            return new RenderedEmail(request.subject(), request.message(), templateRenderer.looksLikeHtml(request.message()));
        }
        // La plantilla se busca dentro de la empresa dueña de la cuenta SMTP.
        EmailTemplate template = templateService.resolveForAccount(account, request.templateName());

        return new RenderedEmail(
                templateRenderer.render(template.getSubject(), request.variables()),
                templateRenderer.render(template.getContent(), request.variables()),
                "html".equalsIgnoreCase(template.getContentType()));
    }

    private static MimeMessage buildMessage(JavaMailSender mailSender, NotificationRequest request,
                                            EmailAccount account, RenderedEmail email)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, request.hasAttachments(),
                StandardCharsets.UTF_8.name());

        String fromEmail = StringUtils.hasText(account.getFromAddress()) ? account.getFromAddress() : account.getUsername();
        if (StringUtils.hasText(account.getFromName())) {
            helper.setFrom(new InternetAddress(fromEmail, account.getFromName()));
        } else {
            helper.setFrom(fromEmail);
        }
        if (StringUtils.hasText(account.getReplyTo())) {
            helper.setReplyTo(account.getReplyTo());
        }

        helper.setTo(request.to());
        helper.setSubject(email.subject() == null ? "" : email.subject());
        helper.setText(email.body(), email.html());

        for (Attachment attachment : request.attachments()) {
            addAttachment(helper, attachment);
        }
        return mimeMessage;
    }

    private static void addAttachment(MimeMessageHelper helper, Attachment attachment) throws MessagingException {
        if (attachment == null || !StringUtils.hasText(attachment.base64Content())) {
            return;
        }
        byte[] content;
        try {
            content = Base64.getDecoder().decode(attachment.base64Content());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Attachment '" + attachment.name() + "' is not valid Base64 content", ex);
        }
        String contentType = StringUtils.hasText(attachment.contentType()) ? attachment.contentType() : DEFAULT_CONTENT_TYPE;
        String name = StringUtils.hasText(attachment.name()) ? attachment.name() : "attachment";
        helper.addAttachment(name, new ByteArrayDataSource(content, contentType));
    }

    /** Message-ID real asignado por JavaMail; cae al identificador sintético si no está disponible. */
    private static String providerMessageId(MimeMessage message, EmailAccount account) {
        try {
            String messageId = message.getMessageID();
            if (StringUtils.hasText(messageId)) {
                return messageId;
            }
        } catch (MessagingException ignored) {
            // sin Message-ID: se usa el identificador sintético de abajo
        }
        return "email-" + account.getClientCode() + "-" + System.currentTimeMillis();
    }

    private record RenderedEmail(String subject, String body, boolean html) {
    }
}
