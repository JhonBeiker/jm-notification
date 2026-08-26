package com.jmcode.notification.channel;


public interface NotificationChannel {

    ChannelType supports();

    boolean isEnabled();

    NotificationResult send(NotificationRequest request);
}
