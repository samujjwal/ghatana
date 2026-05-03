package com.ghatana.digitalmarketing.domain.event;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Canonical registry of DMOS domain event types, their current schema versions,
 * and default PII classifications.
 *
 * <p>The registry is the single source of truth for:</p>
 * <ul>
 *   <li>Which event types are registered for the DMOS product</li>
 *   <li>The current schema version for each event type</li>
 *   <li>The default PII classification for payload routing decisions</li>
 * </ul>
 *
 * <p>Schema version format: {@code MAJOR.MINOR.PATCH} (SemVer).
 * A MAJOR bump requires a migration strategy before deploying consumers.</p>
 *
 * <p>This class is stateless and thread-safe.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS canonical event type registry for schema versioning and PII classification
 * @doc.layer product
 * @doc.pattern Registry
 */
public final class DmEventRegistry {

    /** Current schema version applied to all event types in this release. */
    public static final String SCHEMA_VERSION = "1.0.0";

    /** Source service label used when emitting events from the application layer. */
    public static final String SOURCE_SERVICE_APPLICATION = "dm-application";

    /** Source service label used when emitting events from the API layer. */
    public static final String SOURCE_SERVICE_API = "dm-api";

    private static final Map<DmEventType, DmPiiClassification> PII_BY_TYPE;

    static {
        EnumMap<DmEventType, DmPiiClassification> map = new EnumMap<>(DmEventType.class);

        // Campaign — no personal data in core metadata
        map.put(DmEventType.CAMPAIGN_CREATED,        DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.CAMPAIGN_LAUNCHED,       DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.CAMPAIGN_PAUSED,         DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.CAMPAIGN_COMPLETED,      DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.CAMPAIGN_CANCELLED,      DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.CAMPAIGN_BUDGET_UPDATED, DmPiiClassification.PSEUDONYMOUS);

        // Content
        map.put(DmEventType.CONTENT_CREATED,            DmPiiClassification.NONE);
        map.put(DmEventType.CONTENT_PUBLISHED,          DmPiiClassification.NONE);
        map.put(DmEventType.CONTENT_ARCHIVED,           DmPiiClassification.NONE);
        map.put(DmEventType.CONTENT_VALIDATION_PASSED,  DmPiiClassification.NONE);
        map.put(DmEventType.CONTENT_VALIDATION_FAILED,  DmPiiClassification.NONE);

        // Approval — contains actor/reviewer IDs (pseudonymous)
        map.put(DmEventType.APPROVAL_REQUESTED,  DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.APPROVAL_APPROVED,   DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.APPROVAL_REJECTED,   DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.APPROVAL_ESCALATED,  DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.APPROVAL_EXPIRED,    DmPiiClassification.PSEUDONYMOUS);

        // Consent — involves contact identity
        map.put(DmEventType.CONSENT_CAPTURED,      DmPiiClassification.PERSONAL);
        map.put(DmEventType.CONSENT_REVOKED,       DmPiiClassification.PERSONAL);
        map.put(DmEventType.CONSENT_PROOF_STORED,  DmPiiClassification.PERSONAL);

        // Lead — contains contact PII
        map.put(DmEventType.LEAD_CAPTURED,            DmPiiClassification.PERSONAL);
        map.put(DmEventType.LEAD_QUALIFIED,           DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.LEAD_CONVERTED,           DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.LEAD_DISQUALIFIED,        DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.LEAD_TOUCHPOINT_RECORDED, DmPiiClassification.PERSONAL);

        // Proposal
        map.put(DmEventType.PROPOSAL_CREATED,    DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.PROPOSAL_SUBMITTED,  DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.PROPOSAL_APPROVED,   DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.PROPOSAL_REJECTED,   DmPiiClassification.PSEUDONYMOUS);

        // Workflow — system metadata only
        map.put(DmEventType.WORKFLOW_STARTED,        DmPiiClassification.NONE);
        map.put(DmEventType.WORKFLOW_STEP_COMPLETED, DmPiiClassification.NONE);
        map.put(DmEventType.WORKFLOW_STEP_FAILED,    DmPiiClassification.NONE);
        map.put(DmEventType.WORKFLOW_PAUSED,         DmPiiClassification.NONE);
        map.put(DmEventType.WORKFLOW_RESUMED,        DmPiiClassification.NONE);
        map.put(DmEventType.WORKFLOW_COMPLETED,      DmPiiClassification.NONE);
        map.put(DmEventType.WORKFLOW_FAILED,         DmPiiClassification.NONE);

        // Command
        map.put(DmEventType.COMMAND_CREATED,     DmPiiClassification.NONE);
        map.put(DmEventType.COMMAND_EXECUTED,    DmPiiClassification.NONE);
        map.put(DmEventType.COMMAND_FAILED,      DmPiiClassification.NONE);
        map.put(DmEventType.COMMAND_ROLLED_BACK, DmPiiClassification.NONE);

        // Connector
        map.put(DmEventType.CONNECTOR_CONNECTED,      DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.CONNECTOR_DISCONNECTED,   DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.CONNECTOR_ERROR,          DmPiiClassification.NONE);
        map.put(DmEventType.CONNECTOR_SYNCED,         DmPiiClassification.NONE);
        map.put(DmEventType.CONNECTOR_HEALTH_CHANGED, DmPiiClassification.NONE);

        // Analytics — IP/email in some events
        map.put(DmEventType.PAGE_VIEW,          DmPiiClassification.PERSONAL);
        map.put(DmEventType.FORM_SUBMISSION,    DmPiiClassification.PERSONAL);
        map.put(DmEventType.AD_CLICK,           DmPiiClassification.PERSONAL);
        map.put(DmEventType.CONVERSION,         DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.PERFORMANCE_SYNCED, DmPiiClassification.NONE);

        // Audit / transparency
        map.put(DmEventType.AI_ACTION_RECORDED,    DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.INTAKE_SUBMITTED,      DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.STRATEGY_GENERATED,    DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.BUDGET_APPROVED,       DmPiiClassification.PSEUDONYMOUS);
        map.put(DmEventType.KILL_SWITCH_ACTIVATED, DmPiiClassification.NONE);
        map.put(DmEventType.KILL_SWITCH_DEACTIVATED, DmPiiClassification.NONE);

        PII_BY_TYPE = Collections.unmodifiableMap(map);
    }

    private DmEventRegistry() {}

    /**
     * Returns whether the given event type is registered in the DMOS registry.
     *
     * @param type event type to check, must not be {@code null}
     * @return {@code true} if registered
     */
    public static boolean isRegistered(DmEventType type) {
        return PII_BY_TYPE.containsKey(type);
    }

    /**
     * Returns the default PII classification for the given event type.
     *
     * @param type event type, must not be {@code null}
     * @return PII classification, or {@link Optional#empty()} if not registered
     */
    public static Optional<DmPiiClassification> getPiiClassification(DmEventType type) {
        return Optional.ofNullable(PII_BY_TYPE.get(type));
    }

    /**
     * Returns the current schema version for all registered DMOS events.
     * Individual events may declare a higher minor/patch version in their
     * payload schema when they diverge.
     *
     * @return schema version string, e.g. {@code "1.0.0"}
     */
    public static String currentSchemaVersion() {
        return SCHEMA_VERSION;
    }

    /**
     * Returns an unmodifiable view of the complete registry.
     *
     * @return map from event type to default PII classification
     */
    public static Map<DmEventType, DmPiiClassification> all() {
        return PII_BY_TYPE;
    }

    /**
     * Returns the total number of registered event types.
     *
     * @return registered count
     */
    public static int registeredCount() {
        return PII_BY_TYPE.size();
    }
}
