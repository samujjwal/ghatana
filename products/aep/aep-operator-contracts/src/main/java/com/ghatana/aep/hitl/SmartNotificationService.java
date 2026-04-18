/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.hitl;

import java.util.Map;

/**
 * Service for smart HITL (Human-in-the-Loop) notifications.
 * <p>
 * Determines when human attention is actually needed and sends notifications
 * only when necessary, reducing manual queue checking.
 *
 * @doc.type interface
 * @doc.purpose Smart HITL notification service
 * @doc.layer core
 * @doc.pattern Service
 */
public interface SmartNotificationService {

    /**
     * Determine if a notification should be sent for a HITL item.
     *
     * @param itemId unique item identifier
     * @param priority priority of the item
     * @param context additional context (e.g., tenant, event type, confidence)
     * @return notification decision
     */
    NotificationDecision shouldNotify(String itemId, Priority priority, Map<String, Object> context);

    /**
     * Record user response time for a notification.
     *
     * @param itemId unique item identifier
     * @param responseTimeMs time taken to respond in milliseconds
     */
    void recordResponseTime(String itemId, long responseTimeMs);

    /**
     * Record notification dismissal (no action taken).
     *
     * @param itemId unique item identifier
     * @param reason reason for dismissal
     */
    void recordDismissal(String itemId, String reason);

    /**
     * Get notification statistics for a tenant.
     *
     * @param tenantId tenant identifier
     * @return notification statistics
     */
    NotificationStats getStats(String tenantId);

    /**
     * Notification priority levels.
     */
    enum Priority {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW,
        INFO
    }

    /**
     * Notification decision with metadata.
     */
    record NotificationDecision(
        boolean shouldNotify,
        String channel,
        String reason,
        Map<String, Object> metadata
    ) {}

    /**
     * Notification statistics.
     */
    record NotificationStats(
        long totalSent,
        long totalDismissed,
        long totalResponded,
        double averageResponseTimeMs,
        double notificationRate
    ) {}
}
