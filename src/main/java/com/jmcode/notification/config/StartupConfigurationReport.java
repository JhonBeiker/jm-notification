package com.jmcode.notification.config;

import com.jmcode.notification.email.EmailAccountRepository;
import com.jmcode.notification.telegram.TelegramBotAccountRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/**
 * Informa al arrancar de qué canales quedan realmente operativos.
 *
 * <p>Sustituye a los antiguos seeds que insertaban una cuenta SMTP y un bot de Telegram
 * con valores placeholder: aquella fila quedaba marcada como cuenta por defecto activa,
 * así que el primer envío fallaba con un error de SMTP en lugar de decir que no había
 * ninguna cuenta configurada.
 */
@Component
@RequiredArgsConstructor
public class StartupConfigurationReport implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupConfigurationReport.class);

    private final NotificationProperties properties;
    private final EmailAccountRepository emailAccountRepository;
    private final TelegramBotAccountRepository telegramBotAccountRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (properties.email().enabled()) {
            long accounts = emailAccountRepository.count();
            if (accounts == 0) {
                log.warn("EMAIL channel enabled but no SMTP account exists. "
                        + "Create one with POST /api/v1/admin/email-accounts");
            } else {
                log.info("EMAIL channel enabled with {} SMTP account(s)", accounts);
            }
        } else {
            log.info("EMAIL channel disabled (notification.email.enabled=false)");
        }

        if (properties.telegram().enabled()) {
            long bots = telegramBotAccountRepository.countByActiveTrue();
            if (bots == 0) {
                log.warn("TELEGRAM channel enabled but no active bot account exists. "
                        + "Create one with POST /api/v1/admin/telegram-bot-accounts");
            } else {
                log.info("TELEGRAM channel enabled with {} active bot account(s)", bots);
            }
        } else {
            log.info("TELEGRAM channel disabled (notification.telegram.enabled=false)");
        }

        if (properties.whatsapp().enabled()) {
            log.info("WHATSAPP channel enabled (phoneNumberId configured: {})",
                    !properties.whatsapp().phoneNumberId().isBlank());
        } else {
            log.info("WHATSAPP channel disabled (notification.whatsapp.enabled=false)");
        }
    }
}
