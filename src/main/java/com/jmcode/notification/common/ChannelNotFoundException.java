package com.jmcode.notification.common;

import com.jmcode.notification.channel.ChannelType;

public class ChannelNotFoundException extends RuntimeException {

    public ChannelNotFoundException(ChannelType channel) {
        super("Channel not supported: " + channel);
    }
}
