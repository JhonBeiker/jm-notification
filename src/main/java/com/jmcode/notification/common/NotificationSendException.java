package com.jmcode.notification.common;

import com.jmcode.notification.channel.NotificationResult;

public class NotificationSendException extends RuntimeException {

    private final NotificationResult result;

    public NotificationSendException(NotificationResult result) {
        super(result.detail());
        this.result = result;
    }

    public NotificationResult getResult() {
        return result;
    }
}
