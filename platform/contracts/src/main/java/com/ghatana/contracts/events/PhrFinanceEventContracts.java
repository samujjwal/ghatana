package com.ghatana.contracts.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Canonical event contracts for PHR → Finance integration.
 *
 * <p>These typed records define the wire format for events published by the PHR product
 * and consumed by the Finance product. They are the single source of truth for the
 * cross-product integration contract and must be versioned explicitly.</p>
 *
 * <p>Consumers must tolerate unknown fields (use Jackson's {@code @JsonIgnoreProperties(ignoreUnknown = true)}).
 * Publishers must not remove or rename fields without a deprecation cycle.</p>
 *
 * <p>Schema version: {@code v1}</p>
 *
 * @doc.type class
 * @doc.purpose Canonical typed event contracts for PHR-Finance cross-product integration
 * @doc.layer platform
 * @doc.pattern EventContract
 * @since 1.0.0
 */
public final class PhrFinanceEventContracts {

    private PhrFinanceEventContracts() {}

    // =========================================================================
    // PHR → Finance: Billing events
    // =========================================================================

    /**
     * Published by PHR when a billable healthcare service is rendered.
     *
     * <p>Finance must create a ledger entry upon receiving this event. Idempotency
     * must be enforced using {@link #correlationId} as the ledger transaction ID.</p>
     *
     * @param schemaVersion   always {@code "v1"} for this record
     * @param eventId         unique event ID (UUID)
     * @param correlationId   cross-product correlation ID; used as the idempotency key
     *                        in the Finance billing ledger
     * @param tenantId        the tenant that owns the patient record
     * @param patientId       the patient being billed
     * @param providerId      the provider who rendered the service
     * @param serviceCode     billing service code (e.g. CPT code)
     * @param serviceDate     ISO-8601 date when the service was rendered
     * @param amount          billed amount (positive, non-zero)
     * @param currency        ISO-4217 currency code
     * @param insuranceClaim  whether this is an insurance claim
     * @param occurredAt      when the event was produced
     */
    public record PhrBillingEvent(
            @JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("patientId") String patientId,
            @JsonProperty("providerId") String providerId,
            @JsonProperty("serviceCode") String serviceCode,
            @JsonProperty("serviceDate") String serviceDate,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("insuranceClaim") boolean insuranceClaim,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType
    ) {
        public static final String SCHEMA_VERSION = "v1";
        public static final String EVENT_TYPE = "phr.billing.service-rendered";

        public PhrBillingEvent(
                String schemaVersion,
                String eventId,
                String correlationId,
                String tenantId,
                String patientId,
                String providerId,
                String serviceCode,
                String serviceDate,
                BigDecimal amount,
                String currency,
                boolean insuranceClaim,
                Instant occurredAt
        ) {
            this(schemaVersion, eventId, correlationId, tenantId, patientId, providerId,
                    serviceCode, serviceDate, amount, currency, insuranceClaim, occurredAt, EVENT_TYPE);
        }

        public PhrBillingEvent {
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(patientId, "patientId");
            Objects.requireNonNull(serviceCode, "serviceCode");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(occurredAt, "occurredAt");
            Objects.requireNonNull(eventType, "eventType");
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
        }
    }

    // =========================================================================
    // PHR → Audit: Consent audit events
    // =========================================================================

    /**
     * Published by PHR when a consent decision is made (allowed or denied).
     *
     * <p>This event is consumed by the cross-product audit trail plugin and must not
     * contain raw PHI. The {@link #patientId} is a tenant-scoped pseudonym in prod.</p>
     *
     * @param schemaVersion   always {@code "v1"}
     * @param eventId         unique event ID (UUID)
     * @param correlationId   request-scoped correlation ID for trace linkage
     * @param tenantId        the tenant owning the patient record
     * @param patientId       the patient (pseudonymised in production)
     * @param actorId         the actor requesting access
     * @param actorType       the role of the actor (PROVIDER, PATIENT, ADMIN, etc.)
     * @param action          the action being evaluated
     * @param decision        ALLOWED or DENIED
     * @param reasonCode      machine-readable reason for the decision
     * @param auditRequired   whether a human audit review is required
     * @param occurredAt      when the decision was made
     */
    public record PhrConsentAuditEvent(
            @JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("patientId") String patientId,
            @JsonProperty("actorId") String actorId,
            @JsonProperty("actorType") String actorType,
            @JsonProperty("action") String action,
            @JsonProperty("decision") Decision decision,
            @JsonProperty("reasonCode") String reasonCode,
            @JsonProperty("auditRequired") boolean auditRequired,
            @JsonProperty("occurredAt") Instant occurredAt
    ) {
        public static final String SCHEMA_VERSION = "v1";
        public static final String EVENT_TYPE = "phr.consent.decision-made";

        public enum Decision { ALLOWED, DENIED }

        public PhrConsentAuditEvent {
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(patientId, "patientId");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    // =========================================================================
    // Finance → Risk: Risk-relevant trade events
    // =========================================================================

    /**
     * Published by Finance when a trade or transaction needs risk evaluation.
     *
     * <p>Consumed by the risk management plugin. The {@link #correlationId} must be
     * propagated to all downstream risk alerts and approval requests.</p>
     *
     * @param schemaVersion  always {@code "v1"}
     * @param eventId        unique event ID (UUID)
     * @param correlationId  trade lifecycle correlation ID
     * @param tenantId       the tenant that owns the trade
     * @param entityId       the portfolio or account being evaluated
     * @param tradeId        the originating trade ID
     * @param riskType       the risk type to evaluate (MARKET, CREDIT, LIQUIDITY)
     * @param notionalAmount the notional value of the trade
     * @param currency       ISO-4217 currency code
     * @param occurredAt     when the event was produced
     */
    public record FinanceRiskEvaluationEvent(
            @JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("entityId") String entityId,
            @JsonProperty("tradeId") String tradeId,
            @JsonProperty("riskType") String riskType,
            @JsonProperty("notionalAmount") BigDecimal notionalAmount,
            @JsonProperty("currency") String currency,
            @JsonProperty("occurredAt") Instant occurredAt
    ) {
        public static final String SCHEMA_VERSION = "v1";
        public static final String EVENT_TYPE = "finance.risk.evaluation-requested";

        public FinanceRiskEvaluationEvent {
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(entityId, "entityId");
            Objects.requireNonNull(riskType, "riskType");
            Objects.requireNonNull(notionalAmount, "notionalAmount");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }
}
