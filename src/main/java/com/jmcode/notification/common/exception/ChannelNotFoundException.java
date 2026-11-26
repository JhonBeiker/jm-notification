package com.jmcode.notification.common.exception;

import com.jmcode.notification.channel.ChannelType;

public class ChannelNotFoundException extends RuntimeException {

    public ChannelNotFoundException(ChannelType channel) {
        super("Channel not supported: " + channel);
    }
}
