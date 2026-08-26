package com.jmcode.notification.email;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cachea un {@link JavaMailSender} por cuenta. La entrada se indexa por
 * {@code clientCode} y guarda el {@code updatedAt} con el que se construyó, de forma
 * que al editar la cuenta se <em>reemplaza</em> (y no se acumula, como ocurría al usar
 * {@code clientCode + updatedAt} como clave).
 */
@Service
@RequiredArgsConstructor
public class EmailAccountManager {

    private final Map<String, CachedSender> senderCache = new ConcurrentHashMap<>();
    private final PasswordEncryptor passwordEncryptor;

    public JavaMailSender getSender(EmailAccount account) {
        Instant version = account.getUpdatedAt();
        CachedSender cached = senderCache.compute(account.getClientCode(), (key, current) ->
                current != null && current.matches(version) ? current : new CachedSender(version, createSender(account)));
        return cached.sender();
    }

    public void evict(String clientCode) {
        senderCache.remove(clientCode);
    }

    private JavaMailSender createSender(EmailAccount account) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(account.getHost());
        sender.setPort(account.getPort());
        sender.setUsername(account.getUsername());
        sender.setPassword(passwordEncryptor.decrypt(account.getPassword()));
        sender.setProtocol(account.getProtocol());
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", account.getProtocol());
        props.put("mail.smtp.auth", String.valueOf(account.isAuth()));
        props.put("mail.smtp.starttls.enable", String.valueOf(account.isStarttlsEnable()));
        props.put("mail.smtp.starttls.required", String.valueOf(account.isStarttlsRequired()));
        props.put("mail.smtp.ssl.enable", String.valueOf(account.isSslEnable()));
        props.put("mail.smtp.connectiontimeout", String.valueOf(account.getConnectionTimeoutMs()));
        props.put("mail.smtp.timeout", String.valueOf(account.getTimeoutMs()));
        props.put("mail.smtp.writetimeout", String.valueOf(account.getWriteTimeoutMs()));

        if (account.isSslEnable() && account.getPort() == 465) {
            props.put("mail.smtp.socketFactory.port", "465");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
        }
        return sender;
    }

    private record CachedSender(Instant version, JavaMailSender sender) {
        boolean matches(Instant candidate) {
            return version != null && version.equals(candidate);
        }
    }
}
