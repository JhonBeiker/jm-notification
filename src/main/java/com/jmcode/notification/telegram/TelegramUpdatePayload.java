package com.jmcode.notification.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdatePayload(
        @JsonProperty("update_id") Long updateId,
        TelegramMessage message,
        @JsonProperty("edited_message") TelegramMessage editedMessage,
        @JsonProperty("my_chat_member") TelegramChatMemberUpdated myChatMember
) {
    public TelegramMessage effectiveMessage() {
        return message != null ? message : editedMessage;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramMessage(
            @JsonProperty("message_id") Long messageId,
            TelegramUser from,
            TelegramChat chat,
            Long date,
            String text
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramUser(
            Long id,
            @JsonProperty("is_bot") Boolean isBot,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String username,
            @JsonProperty("language_code") String languageCode
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramChat(
            Long id,
            String type,
            String title,
            String username,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramChatMemberUpdated(
            TelegramChat chat,
            TelegramUser from,
            Long date,
            @JsonProperty("new_chat_member") TelegramChatMember newChatMember
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramChatMember(
            String status,
            TelegramUser user
    ) {
    }
}
