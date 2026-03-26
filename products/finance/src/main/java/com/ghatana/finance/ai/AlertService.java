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
    
    public void sendAlert(String title, String message) {
        logger.warn("ALERT: {} - {}", title, message);
        // TODO: Integrate with actual alerting system (PagerDuty, Slack, etc.)
    }
}
