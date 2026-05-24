package com.ghatana.contracts.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical event contracts for PHR (Personal Health Records) product.
 *
 * <p>These typed records define the wire format for events published by the PHR product
 * and consumed by Kernel, Data Cloud, and other products. They are the single source of truth
 * for PHR event contracts and must be versioned explicitly.</p>
 *
 * <p>Consumers must tolerate unknown fields (use Jackson's {@code @JsonIgnoreProperties(ignoreUnknown = true)}).
 * Publishers must not remove or rename fields without a deprecation cycle.</p>
 *
 * <p>Schema version: {@code v1}</p>
 *
 * @doc.type class
 * @doc.purpose Canonical typed event contracts for PHR product
 * @doc.layer platform
 * @doc.pattern EventContract
 * @since 1.0.0
 */
public final class PhrEventContracts {

    private PhrEventContracts() {}

    // =========================================================================
    // PHR Lifecycle Events
    // =========================================================================

    /**
     * Published by PHR when transitioning between lifecycle phases.
     *
     * <p>This event enables cross-product coordination and evidence collection for
     * regulated healthcare deployments. Kernel consumes this for lifecycle tracking
     * and Data Cloud stores it as durable evidence.</p>
     *
     * @param schemaVersion   always {@code "v1"}
     * @param eventId         unique event ID (UUID)
     * @param correlationId   request-scoped correlation ID for trace linkage
     * @param productId       the product (always "phr")
     * @param phase           lifecycle phase (validate, test, build, package, deploy, verify)
     * @param status          phase status (started, completed, failed)
     * @param runId           lifecycle run identifier
     * @param environment     deployment environment (local, dev, staging, prod)
     * @param tenantId        tenant ID (optional for system-level events)
     * @param occurredAt      when the event was produced
     */
    public record PhrLifecyclePhaseEvent(
            @JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("productId") String productId,
            @JsonProperty("phase") String phase,
            @JsonProperty("status") String status,
            @JsonProperty("runId") String runId,
            @JsonProperty("environment") String environment,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("occurredAt") Instant occurredAt
    ) {
        public static final String SCHEMA_VERSION = "v1";
        public static final String EVENT_TYPE = "phr.lifecycle.phase-transition";

        public PhrLifecyclePhaseEvent {
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(runId, "runId");
            Objects.requireNonNull(environment, "environment");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    // =========================================================================
    // PHR Audit Events
    // =========================================================================

    /**
     * Published by PHR for all PHI access, consent changes, and data modifications.
     *
     * <p>This event enables compliance with Nepal healthcare regulations (Directive 2081,
     * Privacy Act 2075). It provides the audit trail required for regulated healthcare systems.
     * Data Cloud stores this as durable evidence for compliance audits.</p>
     *
     * @param schemaVersion   always {@code "v1"}
     * @param eventId         unique event ID (UUID)
     * @param correlationId   request-scoped correlation ID for trace linkage
     * @param productId       the product (always "phr")
     * @param auditType       type of audit (patient-access, consent-change, emergency-access, data-modification)
     * @param action          the action performed (read, write, delete, grant, revoke)
     * @param resourceType    type of resource accessed (patient, consent, document, medication)
     * @param resourceId      ID of the resource
     * @param actorId         ID of the actor performing the action
     * @param actorRole       role of the actor (PROVIDER, PATIENT, ADMIN, SYSTEM)
     * @param tenantId        tenant ID
     * @param patientId       patient ID (pseudonymised in production)
     * @param metadata        additional context (optional)
     * @param occurredAt      when the event was produced
     */
    public record PhrAuditTrailEvent(
            @JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("productId") String productId,
            @JsonProperty("auditType") String auditType,
            @JsonProperty("action") String action,
            @JsonProperty("resourceType") String resourceType,
            @JsonProperty("resourceId") String resourceId,
            @JsonProperty("actorId") String actorId,
            @JsonProperty("actorRole") String actorRole,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("patientId") String patientId,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("occurredAt") Instant occurredAt
    ) {
        public static final String SCHEMA_VERSION = "v1";
        public static final String EVENT_TYPE = "phr.audit.trail";

        public PhrAuditTrailEvent {
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(auditType, "auditType");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(resourceType, "resourceType");
            Objects.requireNonNull(resourceId, "resourceId");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(actorRole, "actorRole");
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(occurredAt, "occurredAt");
            if (metadata == null) {
                metadata = Map.of();
            }
        }
    }

    // =========================================================================
    // PHR Consent Events
    // =========================================================================

    /**
     * Published by PHR when patient consent is granted, revoked, or modified.
     *
     * <p>This event triggers cache invalidation across distributed nodes and provides
     * the evidence trail for healthcare compliance. Data Cloud stores this as durable
     * evidence and Kernel uses it for consent state synchronization.</p>
     *
     * @param schemaVersion   always {@code "v1"}
     * @param eventId         unique event ID (UUID)
     * @param correlationId   request-scoped correlation ID for trace linkage
     * @param productId       the product (always "phr")
     * @param consentType     type of consent (data-access, emergency-access, treatment)
     * @param action          consent action (granted, revoked, modified, expired)
     * @param patientId       patient ID
     * @param recipientId     ID of the consent recipient
     * @param resourceType    type of resource consent applies to
     * @param purpose         purpose of the consent
     * @param expiresAt       consent expiration time (null for indefinite)
     * @param tenantId        tenant ID
     * @param metadata        additional context (optional)
     * @param occurredAt      when the event was produced
     */
    public record PhrConsentChangeEvent(
            @JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("productId") String productId,
            @JsonProperty("consentType") String consentType,
            @JsonProperty("action") String action,
            @JsonProperty("patientId") String patientId,
            @JsonProperty("recipientId") String recipientId,
            @JsonProperty("resourceType") String resourceType,
            @JsonProperty("purpose") String purpose,
            @JsonProperty("expiresAt") Instant expiresAt,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("occurredAt") Instant occurredAt
    ) {
        public static final String SCHEMA_VERSION = "v1";
        public static final String EVENT_TYPE = "phr.consent.change";

        public PhrConsentChangeEvent {
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(consentType, "consentType");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(patientId, "patientId");
            Objects.requireNonNull(recipientId, "recipientId");
            Objects.requireNonNull(resourceType, "resourceType");
            Objects.requireNonNull(purpose, "purpose");
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(occurredAt, "occurredAt");
            if (metadata == null) {
                metadata = Map.of();
            }
        }
    }

    // =========================================================================
    // PHR Emergency Access Events
    // =========================================================================

    /**
     * Published by PHR when emergency access is granted (break-glass).
     *
     * <p>This event is critical for healthcare compliance and requires immediate
     * patient notification and post-hoc review. Data Cloud stores this as durable
     * evidence for regulatory audits.</p>
     *
     * @param schemaVersion   always {@code "v1"}
     * @param eventId         unique event ID (UUID)
     * @param correlationId   request-scoped correlation ID for trace linkage
     * @param productId       the product (always "phr")
     * @param patientId       patient ID
     * @param accessorId      ID of the actor requesting emergency access
     * @param accessorRole    role of the accessor
     * @param reasonCode      machine-readable reason for emergency access
     * @param reasonText      human-readable explanation
     * @param tenantId        tenant ID
     * @param notificationSent whether patient notification was sent
     * @param reviewRequired whether post-hoc review is required
     * @param occurredAt      when the event was produced
     */
    public record PhrEmergencyAccessEvent(
            @JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("productId") String productId,
            @JsonProperty("patientId") String patientId,
            @JsonProperty("accessorId") String accessorId,
            @JsonProperty("accessorRole") String accessorRole,
            @JsonProperty("reasonCode") String reasonCode,
            @JsonProperty("reasonText") String reasonText,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("notificationSent") boolean notificationSent,
            @JsonProperty("reviewRequired") boolean reviewRequired,
            @JsonProperty("occurredAt") Instant occurredAt
    ) {
        public static final String SCHEMA_VERSION = "v1";
        public static final String EVENT_TYPE = "phr.emergency.access-granted";

        public PhrEmergencyAccessEvent {
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(patientId, "patientId");
            Objects.requireNonNull(accessorId, "accessorId");
            Objects.requireNonNull(accessorRole, "accessorRole");
            Objects.requireNonNull(reasonCode, "reasonCode");
            Objects.requireNonNull(reasonText, "reasonText");
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }
}
