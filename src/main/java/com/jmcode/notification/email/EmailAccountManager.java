package com.jmcode.notification.email;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmailAccountManager {

    private final Map<String, JavaMailSender> senderCache = new ConcurrentHashMap<>();
    private final PasswordEncryptor passwordEncryptor;

    public EmailAccountManager(PasswordEncryptor passwordEncryptor) {
        this.passwordEncryptor = passwordEncryptor;
    }

    public JavaMailSender getSender(EmailAccount account) {
        String cacheKey = account.getClientCode() + "_" + account.getUpdatedAt().toEpochMilli();
        return senderCache.computeIfAbsent(cacheKey, key -> createSender(account));
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
}

