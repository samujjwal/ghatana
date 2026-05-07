package com.ghatana.digitalmarketing.bridge;

import com.ghatana.plugin.notification.NotificationPlugin;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Routes DMOS notifications to the appropriate channel based on the template prefix.
 *
 * <p>Routing rules:
 * <ul>
 *   <li>{@code email.*} → email channel (e.g. {@code email.campaign-launched})</li>
 *   <li>{@code sms.*} → SMS channel (e.g. {@code sms.budget-alert})</li>
 *   <li>{@code push.*} → push notification channel (e.g. {@code push.ai-action-complete})</li>
 *   <li>any other prefix → default channel (dispatched as-is)</li>
 * </ul>
 *
 * <p>Canonical DMOS template constants are exposed as {@code public static final}
 * fields for compile-time safety.
 *
 * @doc.type class
 * @doc.purpose Multi-channel notification router for DMOS events (KERNEL-P1-3)
 * @doc.layer product
 * @doc.pattern Adapter, Router
 */
public final class DmosNotificationChannelRouter {

    private static final Logger LOG = LoggerFactory.getLogger(DmosNotificationChannelRouter.class);

    // -------------------------------------------------------------------------
    // Canonical DMOS template identifiers
    // -------------------------------------------------------------------------

    /** Email — campaign successfully launched. */
    public static final String EMAIL_CAMPAIGN_LAUNCHED     = "email.campaign-launched";

    /** Email — campaign paused by kill-switch or operator. */
    public static final String EMAIL_CAMPAIGN_PAUSED       = "email.campaign-paused";

    /** Email — DSAR right-to-erasure completed. */
    public static final String EMAIL_DSAR_ERASURE_COMPLETE = "email.dsar-erasure-complete";

    /** SMS — budget threshold alert. */
    public static final String SMS_BUDGET_ALERT            = "sms.budget-alert";

    /** SMS — ad-spend anomaly detected. */
    public static final String SMS_AD_SPEND_ANOMALY        = "sms.ad-spend-anomaly";

    /** Push — AI-driven action completed. */
    public static final String PUSH_AI_ACTION_COMPLETE     = "push.ai-action-complete";

    /** Push — human approval required. */
    public static final String PUSH_APPROVAL_REQUIRED      = "push.approval-required";

    // -------------------------------------------------------------------------

    private final NotificationPlugin notificationPlugin;

    /**
     * Creates a router backed by the provided notification plugin.
     *
     * @param notificationPlugin the plugin to delegate dispatch to
     */
    public DmosNotificationChannelRouter(NotificationPlugin notificationPlugin) {
        this.notificationPlugin = Objects.requireNonNull(notificationPlugin,
            "notificationPlugin must not be null");
    }

    /**
     * Dispatches a notification to the appropriate channel and logs the routing decision.
     *
     * @param recipientId the recipient identifier (e.g. contact ID or user ID)
     * @param template    the DMOS template identifier (e.g. {@code email.campaign-launched})
     * @param attributes  template substitution attributes
     * @return a promise resolving to the notification ID
     */
    public Promise<String> dispatch(String recipientId, String template, Map<String, String> attributes) {
        Objects.requireNonNull(recipientId, "recipientId must not be null");
        Objects.requireNonNull(template, "template must not be null");
        Objects.requireNonNull(attributes, "attributes must not be null");

        String channel = resolveChannel(template);
        LOG.info("[DMOS] Routing notification: recipientId={} template={} channel={}",
            recipientId, template, channel);

        return notificationPlugin.dispatch(recipientId, template, attributes)
            .map(notificationId -> {
                LOG.info("[DMOS] Notification dispatched: notificationId={} channel={} template={}",
                    notificationId, channel, template);
                return notificationId;
            })
            .mapException(ex -> {
                LOG.error("[DMOS] Notification dispatch failed: recipientId={} template={} channel={} error={}",
                    recipientId, template, channel, ex.getMessage());
                return ex;
            });
    }

    /**
     * Resolves the logical channel name from the template prefix.
     *
     * @param template the template identifier
     * @return a human-readable channel name for logging
     */
    public static String resolveChannel(String template) {
        if (template == null) return "unknown";
        if (template.startsWith("email.")) return "email";
        if (template.startsWith("sms.")) return "sms";
        if (template.startsWith("push.")) return "push";
        return "default";
    }
}
