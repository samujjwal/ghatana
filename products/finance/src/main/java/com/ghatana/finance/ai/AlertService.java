/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Business logic service for AlertService
 *
 * @doc.type class
 * @doc.purpose Business logic service for AlertService
 * @doc.layer product
 * @doc.pattern Service
 */
public class AlertService {

    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);

    private final AlertChannel alertChannel;

    public AlertService() {
        this(new LoggerAlertChannel());
    }

    public AlertService(AlertChannel alertChannel) {
        this.alertChannel = java.util.Objects.requireNonNull(alertChannel, "alertChannel must not be null");
    }

    public void sendAlert(String title, String message) {
        alertChannel.publish(title, message);
    }

    @FunctionalInterface
    public interface AlertChannel {
        void publish(String title, String message);
    }

    private static final class LoggerAlertChannel implements AlertChannel {
        @Override
        public void publish(String title, String message) {
            logger.warn("ALERT: {} - {}", title, message);
        }
    }
}
