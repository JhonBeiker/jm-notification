package com.jmcode.notification.channel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Attachment(

        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 128)
        String contentType,

        @NotBlank
        @Size(max = 13_981_014, message = "attachment exceeds the 10 MB limit")
        String base64Content
) {
}
