package com.jmcode.notification.web.error;

import com.jmcode.notification.domain.NotificationResult;

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
