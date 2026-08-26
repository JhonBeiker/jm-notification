package com.jmcode.notification.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TelegramWebhookService {

    private static final String WEBHOOK_PATH = "/api/v1/telegram/webhook";

    private final TelegramBotAccountService accountService;
    private final TelegramBotAccountManager accountManager;

    public Map<String, Object> register(String publicBaseUrl, String clientCode) {
        TelegramBotAccount account = accountService.resolveAccount(clientCode);
        String webhookUrl = resolveWebhookUrl(account, publicBaseUrl);
        requireHttps(webhookUrl);

        Map<String, Object> telegramResponse = accountManager.getClient(account)
                .setWebhook(webhookUrl, account.getWebhookSecret());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientCode", account.getClientCode());
        result.put("webhookUrl", webhookUrl);
        result.put("secretConfigured", StringUtils.hasText(account.getWebhookSecret()));
        result.put("telegram", telegramResponse);
        return result;
    }

    public Map<String, Object> unregister(String clientCode) {
        TelegramBotAccount account = accountService.resolveAccount(clientCode);
        Map<String, Object> telegramResponse = accountManager.getClient(account).deleteWebhook();
        return Map.of("clientCode", account.getClientCode(), "telegram", telegramResponse);
    }

    public Map<String, Object> info(String clientCode) {
        TelegramBotAccount account = accountService.resolveAccount(clientCode);
        Map<String, Object> telegramResponse = accountManager.getClient(account).getWebhookInfo();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientCode", account.getClientCode());
        result.put("configuredWebhookUrl", account.getWebhookUrl());
        result.put("autoRegister", account.isWebhookAutoRegister());
        result.put("secretConfigured", StringUtils.hasText(account.getWebhookSecret()));
        result.put("telegram", telegramResponse);
        return result;
    }

    public Map<String, Object> botInfo(String clientCode) {
        TelegramBotAccount account = accountService.resolveAccount(clientCode);
        return accountManager.getClient(account).getMe();
    }

    private static String resolveWebhookUrl(TelegramBotAccount account, String publicBaseUrl) {
        if (StringUtils.hasText(publicBaseUrl)) {
            return join(publicBaseUrl.trim(), WEBHOOK_PATH);
        }
        if (StringUtils.hasText(account.getWebhookUrl())) {
            String configured = account.getWebhookUrl().trim();
            return configured.endsWith(WEBHOOK_PATH) ? configured : join(configured, WEBHOOK_PATH);
        }
        throw new IllegalArgumentException(
                "Webhook URL required. Pass publicBaseUrl in the request body or set webhookUrl in the bot account");
    }

    /** Telegram rechaza webhooks que no sean HTTPS: se avisa antes de la llamada remota. */
    private static void requireHttps(String webhookUrl) {
        if (!webhookUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Telegram requires an HTTPS webhook URL, got: " + webhookUrl);
        }
    }

    private static String join(String base, String path) {
        String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return normalized + path;
    }
}
