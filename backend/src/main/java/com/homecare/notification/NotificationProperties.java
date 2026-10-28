package com.homecare.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(@DefaultValue("email") AlertChannel channel) {}
