package com.jmcode.notification.channel;

import com.jmcode.notification.domain.ChannelType;
import com.jmcode.notification.domain.NotificationRequest;
import com.jmcode.notification.domain.NotificationResult;

public interface NotificationChannel {

    ChannelType supports();

    boolean isEnabled();

    NotificationResult send(NotificationRequest request);
}
