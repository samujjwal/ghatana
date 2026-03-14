package com.ghatana.appplatform.config.approval;

import com.ghatana.appplatform.config.domain.ConfigHierarchyLevel;
import com.ghatana.appplatform.config.notification.ConfigChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dispatches structured notifications around the config maker-checker approval lifecycle.
 *
 * <h2>Events emitted</h2>
 * <ol>
 *   <li><strong>Pending approval</strong> — a maker has submitted a proposal; checkers must
 *       be notified. Call {@link #notifyPending(PendingApprovalEvent)} from the proposal
 *       submission path.</li>
 *   <li><strong>Approval decision</strong> — a checker has approved or rejected a proposal;
 *       the maker and interested parties should be informed. Call
 *       {@link #notifyDecided(ApprovalDecisionEvent)} from the decision path.</li>
 *   <li><strong>Live config change</strong> — an approved change has been applied and is now
 *       active. Received via {@link ConfigChangeListener#onConfigChange} when registered with
 *       a {@link com.ghatana.appplatform.config.notification.ConfigChangeNotifier}.</li>
 * </ol>
 *
 * <h2>Sensitive namespace gating</h2>
 * <p>Namespaces declared as <em>sensitive</em> at construction time (e.g.
 * {@code "security"}, {@code "payments"}) trigger an additional warning log when a live
 * change fires without an associated approval event. This surfaces bypassed approvals
 * in the monitoring layer.
 *
 * <h2>Handler isolation</h2>
 * <p>Each registered {@link ApprovalEventHandler} is invoked in isolation. An exception
 * thrown by one handler is logged and suppressed so that other handlers are still called.
 *
 * @doc.type class
 * @doc.purpose Approval lifecycle notification dispatcher for config maker-checker workflow (STORY-K02-015)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ConfigApprovalNotificationService implements ConfigChangeListener {

    private static final Logger log =
        LoggerFactory.getLogger(ConfigApprovalNotificationService.class);

    // ── Public model types ────────────────────────────────────────────────────

    /**
     * Approval decision outcomes.
     */
    public enum Decision { APPROVED, REJECTED }

    /**
     * Event emitted when a config change proposal enters the PENDING state.
     *
     * @param proposalId  unique proposal identifier
     * @param tenantId    tenant scope of the proposal
     * @param namespace   config namespace being changed
     * @param key         config key being changed
     * @param level       hierarchy level of the change
     * @param levelId     scope identifier for the level
     * @param proposedBy  user ID of the maker
     * @param proposedAt  timestamp when the proposal was submitted
     */
    public record PendingApprovalEvent(
            String proposalId,
            String tenantId,
            String namespace,
            String key,
            ConfigHierarchyLevel level,
            String levelId,
            String proposedBy,
            Instant proposedAt
    ) {
        public PendingApprovalEvent {
            Objects.requireNonNull(proposalId, "proposalId");
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(levelId, "levelId");
            Objects.requireNonNull(proposedBy, "proposedBy");
            Objects.requireNonNull(proposedAt, "proposedAt");
        }
    }

    /**
     * Event emitted when a checker makes a decision on a pending proposal.
     *
     * @param proposalId  unique proposal identifier
     * @param tenantId    tenant scope of the original proposal
     * @param namespace   config namespace that was changed
     * @param key         config key that was changed
     * @param decision    outcome of the review
     * @param decidedBy   user ID of the checker
     * @param reason      optional rejection reason (may be {@code null} for approvals)
     * @param decidedAt   timestamp of the decision
     */
    public record ApprovalDecisionEvent(
            String proposalId,
            String tenantId,
            String namespace,
            String key,
            Decision decision,
            String decidedBy,
            String reason,
            Instant decidedAt
    ) {
        public ApprovalDecisionEvent {
            Objects.requireNonNull(proposalId, "proposalId");
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(decidedBy, "decidedBy");
            Objects.requireNonNull(decidedAt, "decidedAt");
            // reason may be null for approved proposals
        }
    }

    /**
     * Notification handler for config approval lifecycle events.
     *
     * <p>Implementations are called on the thread that triggered the event and must
     * return quickly. Dispatch long-running work (email, webhook) to a dedicated executor.
     */
    public interface ApprovalEventHandler {

        /**
         * Called when a config change proposal is pending checker review.
         *
         * @param event proposal details
         */
        void onApprovalRequired(PendingApprovalEvent event);

        /**
         * Called when a proposal has been approved or rejected.
         *
         * @param event decision details
         */
        void onApprovalDecided(ApprovalDecisionEvent event);

        /**
         * Called when an approved config change becomes live (fired via
         * {@link ConfigChangeListener#onConfigChange}).
         *
         * <p>The default implementation is a no-op — override when you need to react
         * to live propagation events (e.g. cache invalidation notifications).
         *
         * @param namespace  config namespace that changed
         * @param key        config key that changed
         * @param level      hierarchy level
         * @param levelId    scope identifier
         */
        default void onConfigLive(String namespace, String key, String level, String levelId) { }
    }

    // ── State ────────────────────────────────────────────────────────────────

    private final List<ApprovalEventHandler> handlers = new CopyOnWriteArrayList<>();

    /**
     * Namespaces that require mandatory approval (e.g. "security", "payments").
     * A live change in one of these namespaces without a prior approval event will log
     * a warning.
     */
    private final Set<String> sensitiveNamespaces;

    // ── Construction ─────────────────────────────────────────────────────────

    /**
     * Creates a notification service with no pre-configured sensitive namespaces.
     */
    public ConfigApprovalNotificationService() {
        this(Set.of());
    }

    /**
     * Creates a notification service with a set of sensitive namespaces that require
     * mandatory approval gating.
     *
     * @param sensitiveNamespaces namespaces where live changes without approval are warned
     */
    public ConfigApprovalNotificationService(Set<String> sensitiveNamespaces) {
        this.sensitiveNamespaces = Set.copyOf(
            Objects.requireNonNull(sensitiveNamespaces, "sensitiveNamespaces"));
    }

    // ── Handler registration ──────────────────────────────────────────────────

    /**
     * Register a handler to receive approval lifecycle events.
     *
     * @param handler handler to add (non-null)
     */
    public void addHandler(ApprovalEventHandler handler) {
        handlers.add(Objects.requireNonNull(handler, "handler"));
    }

    /**
     * Remove a previously registered handler.
     *
     * @param handler handler to remove
     */
    public void removeHandler(ApprovalEventHandler handler) {
        handlers.remove(handler);
    }

    // ── Notification dispatch ─────────────────────────────────────────────────

    /**
     * Notify all registered handlers that a proposal is pending approval.
     *
     * <p>Call this from the proposal submission path immediately after
     * {@link ConfigChangeApprovalService#propose} resolves.
     *
     * @param event event describing the pending proposal
     */
    public void notifyPending(PendingApprovalEvent event) {
        Objects.requireNonNull(event, "event");
        log.info("[ConfigApproval] Pending review: proposalId={} namespace={} key={} by={}",
            event.proposalId(), event.namespace(), event.key(), event.proposedBy());
        for (ApprovalEventHandler handler : handlers) {
            safeCall(handler, () -> handler.onApprovalRequired(event));
        }
    }

    /**
     * Notify all registered handlers of an approval decision (approved or rejected).
     *
     * <p>Call this from the checkout path immediately after
     * {@link ConfigChangeApprovalService#approve} or {@link ConfigChangeApprovalService#reject}
     * resolves.
     *
     * @param event event describing the decision
     */
    public void notifyDecided(ApprovalDecisionEvent event) {
        Objects.requireNonNull(event, "event");
        log.info("[ConfigApproval] Decision: proposalId={} decision={} by={}",
            event.proposalId(), event.decision(), event.decidedBy());
        for (ApprovalEventHandler handler : handlers) {
            safeCall(handler, () -> handler.onApprovalDecided(event));
        }
    }

    /**
     * Returns {@code true} when the given namespace is configured as sensitive (requiring
     * approval gating).
     *
     * @param namespace the config namespace to check
     * @return true if namespace was declared sensitive at construction time
     */
    public boolean isSensitiveNamespace(String namespace) {
        return namespace != null && sensitiveNamespaces.contains(namespace);
    }

    // ── ConfigChangeListener (live propagation) ───────────────────────────────

    /**
     * Receives live config change events from
     * {@link com.ghatana.appplatform.config.notification.ConfigChangeNotifier}.
     *
     * <p>When a sensitive namespace change arrives, a warning is logged to surface
     * live changes that may have bypassed the approval workflow.
     */
    @Override
    public void onConfigChange(String namespace, String key, String level, String levelId) {
        if (isSensitiveNamespace(namespace)) {
            log.warn("[ConfigApproval] LIVE change in sensitive namespace '{}' key='{}' "
                + "level={} levelId={} — verify this was approved via the proposal workflow.",
                namespace, key, level, levelId);
        } else {
            log.debug("[ConfigApproval] Live config change: namespace={} key={}", namespace, key);
        }
        for (ApprovalEventHandler handler : handlers) {
            safeCall(handler, () -> handler.onConfigLive(namespace, key, level, levelId));
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void safeCall(ApprovalEventHandler handler, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("[ConfigApproval] Handler {} threw an exception; suppressed to protect other handlers.",
                handler.getClass().getSimpleName(), e);
        }
    }
}
