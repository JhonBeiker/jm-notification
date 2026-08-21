package com.jmcode.notification.web.error;

import com.jmcode.notification.domain.ChannelType;

public class ChannelNotFoundException extends RuntimeException {

    public ChannelNotFoundException(ChannelType channel) {
        super("Channel not supported: " + channel);
    }
}
