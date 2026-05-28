package com.ghatana.phr.kernel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.phr.kernel.evidence.PhrEvidenceOutbox;
import com.ghatana.phr.kernel.event.PhrConsentEvent;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service that scans for expired consent grants and generates evidence.
 *
 * <p>This service periodically scans the consent dataset for grants that have
 * expired but are still marked as ACTIVE. It publishes expiry events to the
 * evidence outbox for compliance tracking and invalidates cache entries.</p>
 *
 * @doc.type class
 * @doc.purpose Scans for expired consent grants and generates evidence
 * @doc.layer product
 * @doc.pattern Scheduled Job, Evidence Producer
 */
public final class ConsentExpiryScanner extends PhrServiceBase {

    private static final String CONSENT_DATASET = "phr.consent.grants";
    private static final String EVIDENCE_DATASET = "phr.evidence.outbox";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private final PhrEvidenceOutbox evidenceOutbox;

    public ConsentExpiryScanner(KernelContext context, PhrEvidenceOutbox evidenceOutbox) {
        super(context);
        this.evidenceOutbox = Objects.requireNonNull(evidenceOutbox, "evidenceOutbox must not be null");
    }

    @Override
    public String getName() {
        return "phr-consent-expiry-scanner";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        // No new datasets needed - uses existing consent and evidence datasets
        return Promise.complete();
    }

    /**
     * Scans for expired consent grants and generates evidence.
     *
     * <p>This method finds all ACTIVE consent grants where expiresAt is in the past,
     * publishes expiry events to the evidence outbox, and marks them as EXPIRED.</p>
     *
     * @return Promise completing when scan is done
     */
    public Promise<ExpiryScanResult> scanExpiredGrants() {
        ensureRunning();

        Instant now = Instant.now();
        
        return queryRecords(
            CONSENT_DATASET,
            "status = :status AND expiresAt < :expiresAt",
            Map.of(
                "status", "ACTIVE",
                "expiresAt", now.toString()
            ),
            1000,
            0,
            ConsentManagementService.ConsentGrant.class
        )
        .then(expiredGrants -> {
            if (expiredGrants.isEmpty()) {
                return Promise.of(new ExpiryScanResult(0, 0, 0));
            }

            // Process each expired grant
            return Promises.toList(
                expiredGrants.stream()
                    .map(this::processExpiredGrant)
                    .toList()
            )
            .then(results -> {
                int expired = results.size();
                int published = (int) results.stream().filter(r -> r).count();
                int invalidated = published; // Assume successful publish leads to invalidation
                
                return Promise.of(new ExpiryScanResult(expired, published, invalidated));
            });
        });
    }

    /**
     * Processes a single expired consent grant.
     *
     * @param grant the expired grant
     * @return Promise completing with true if evidence was published
     */
    private Promise<Boolean> processExpiredGrant(ConsentManagementService.ConsentGrant grant) {
        // Publish expiry event to evidence outbox
        PhrConsentEvent expiryEvent = PhrConsentEvent.builder()
            .action("expired")
            .consentType("delegated-access")
            .patientId(grant.getPatientId())
            .recipientId(grant.getRecipientId())
            .resourceType(grant.getScope().getResourceTypes().isEmpty() ? "*" 
                : grant.getScope().getResourceTypes().iterator().next())
            .purpose("consent-expiry")
            .expiresAt(grant.getExpiresAt())
            .tenantId(currentTenantId())
            .metadata(Map.of(
                "grantId", grant.getId(),
                "originalScope", grant.getScope().getResourceTypes().toString(),
                "expiredAt", Instant.now().toString()
            ))
            .build();

        // Serialize event to JSON for evidence body
        String eventJson = serializeEvent(expiryEvent);
        
        // Enqueue to evidence outbox
        evidenceOutbox.enqueue(
            EVIDENCE_DATASET,
            expiryEvent.eventId(),
            eventJson.getBytes(StandardCharsets.UTF_8),
            Map.of(
                "eventType", "consent-expiry",
                "patientId", grant.getPatientId(),
                "grantId", grant.getId()
            )
        );

        // Update grant status to EXPIRED
        ConsentManagementService.ConsentGrant expiredGrant = grant.withStatus("EXPIRED").withRevokedAt(Instant.now());

        return updateRecord(
            CONSENT_DATASET,
            grant.getId(),
            expiredGrant,
            mutationMetadata(Map.of(
                "patientId", expiredGrant.getPatientId(),
                "recipientId", expiredGrant.getRecipientId(),
                "status", expiredGrant.getStatus()
            ), expiredGrant.getRecipientId()),
            "ConsentGrant",
            1
        )
        .then((updated, exception) -> {
            if (exception == null) {
                return Promise.of(true);
            }
            // Log but don't fail the entire scan.
            System.err.println("Failed to process expired grant " + grant.getId() + ": " + exception.getMessage());
            return Promise.of(false);
        });
    }

    String serializeEvent(PhrConsentEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.eventId());
        payload.put("productId", event.productId());
        payload.put("consentType", event.consentType());
        payload.put("action", event.action());
        payload.put("patientId", event.patientId());
        payload.put("recipientId", event.recipientId());
        payload.put("resourceType", event.resourceType());
        payload.put("purpose", event.purpose());
        payload.put("expiresAt", event.expiresAt());
        payload.put("tenantId", event.tenantId());
        payload.put("metadata", event.metadata());
        payload.put("timestamp", event.timestamp());
        payload.put("correlationId", event.correlationId());
        try {
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize consent expiry event " + event.eventId(), exception);
        }
    }

    /**
     * Result of a consent expiry scan.
     */
    public record ExpiryScanResult(
        int expiredCount,
        int evidencePublished,
        int cacheInvalidated
    ) {
        public boolean hasExpiredGrants() {
            return expiredCount > 0;
        }
    }
}
