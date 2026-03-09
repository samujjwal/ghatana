package com.ghatana.products.yappc.domain.enums;

/**
 * Enumeration of notification channel types in the YAPPC platform.
 *
 * <p>This enum defines the available channels for sending security
 * alerts, incident notifications, and compliance reports.</p>
 *
 * @doc.type enum
 * @doc.purpose Defines the available notification delivery channels
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum NotificationChannelType {

    /**
     * Email notifications.
     */
    EMAIL("Email", true),

    /**
     * Slack channel/DM notifications.
     */
    SLACK("Slack", true),

    /**
     * Microsoft Teams channel notifications.
     */
    TEAMS("Microsoft Teams", true),

    /**
     * Custom webhook endpoint.
     */
    WEBHOOK("Webhook", false),

    /**
     * PagerDuty incident alerts.
     */
    PAGERDUTY("PagerDuty", true),

    /**
     * Opsgenie incident alerts.
     */
    OPSGENIE("Opsgenie", true),

    /**
     * Jira ticket creation.
     */
    JIRA("Jira", false),

    /**
     * SMS text message.
     */
    SMS("SMS", true);

    private final String displayName;
    private final boolean supportsRichContent;

    NotificationChannelType(String displayName, boolean supportsRichContent) {
        this.displayName = displayName;
        this.supportsRichContent = supportsRichContent;
    }

    /**
     * Returns the human-readable display name.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if this channel supports rich content (formatting, links, etc.).
     *
     * @return true if rich content is supported
     */
    public boolean supportsRichContent() {
        return supportsRichContent;
    }

    /**
     * Checks if this is an incident management integration.
     *
     * @return true if incident management
     */
    public boolean isIncidentManagement() {
        return this == PAGERDUTY || this == OPSGENIE;
    }

    /**
     * Checks if this is a real-time messaging channel.
     *
     * @return true if real-time messaging
     */
    public boolean isRealTimeMessaging() {
        return this == SLACK || this == TEAMS || this == SMS;
    }
}
