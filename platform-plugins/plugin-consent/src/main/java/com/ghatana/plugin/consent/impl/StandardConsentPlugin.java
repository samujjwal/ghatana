package com.ghatana.plugin.consent.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.plugin.consent.ConsentPlugin;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Standard implementation of ConsentPlugin.
 *
 * <p>This implementation provides:</p>
 * <ul>
 *   <li>Consent recording and verification</li>
 *   <li>Consent history tracking</li>
 *   <li>Automatic expiration handling</li>
 *   <li>Revocation support</li>
 * </ul>
 *
 * <p>Supports healthcare (PHR), financial (Finance), and general use cases.</p>
 *
 * @doc.type class
 * @doc.purpose Standard consent management implementation
 * @doc.layer platform
 * @doc.pattern Plugin Implementation
 * @since 1.0.0
 */
public class StandardConsentPlugin implements ConsentPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(StandardConsentPlugin.class);

    private final Map<String, ConsentRecord> consents = new ConcurrentHashMap<>();
    private final Map<String, List<ConsentRecord>> subjectHistory = new ConcurrentHashMap<>();
    private static final PluginMetadata METADATA = PluginMetadata.builder()
        .id("com.ghatana.plugin.consent")
        .name("Consent Plugin")
        .version("1.0.0")
        .description("Consent recording, verification, and revocation plugin")
        .type(PluginType.GOVERNANCE)
        .author("Ghatana")
        .license("Proprietary")
        .capability("consent:record", "consent:verify", "consent:revoke")
        .build();

    private PluginContext context;
    private PluginState state = PluginState.UNLOADED;

    public String getId() {
        return "consent-plugin";
    }

    public String getName() {
        return "Consent Plugin";
    }

    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public PluginMetadata metadata() {
        return METADATA;
    }

    @Override
    public PluginState getState() {
        return state;
    }

    @Override
    public Promise<Void> initialize(PluginContext context) {
        this.context = context;
        this.state = PluginState.INITIALIZED;
        LOG.info("ConsentPlugin initialized");
        return Promise.complete();
    }

    @Override
    public Promise<Void> start() {
        this.state = PluginState.RUNNING;
        LOG.info("ConsentPlugin started");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        this.state = PluginState.STOPPED;
        LOG.info("ConsentPlugin stopped");
        return Promise.complete();
    }

    @Override
    public Promise<Void> shutdown() {
        consents.clear();
        subjectHistory.clear();
        this.state = PluginState.UNLOADED;
        LOG.info("ConsentPlugin shutdown");
        return Promise.complete();
    }

    @Override
    public Promise<ConsentRecord> recordConsent(String subjectId, String purpose, ConsentAction action) {
        String consentId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        ConsentStatus status = switch (action) {
            case GRANT -> ConsentStatus.GRANTED;
            case DENY -> ConsentStatus.DENIED;
            case WITHDRAW -> ConsentStatus.REVOKED;
        };

        ConsentRecord record = new ConsentRecord(
            consentId,
            subjectId,
            purpose,
            status,
            action,
            determineLegalBasis(purpose),
            now,
            calculateExpiry(purpose, now),
            action == ConsentAction.WITHDRAW ? now : null,
            buildMetadata(subjectId, purpose, action)
        );

        consents.put(consentId, record);
        subjectHistory.computeIfAbsent(subjectId, k -> new ArrayList<>()).add(record);

        LOG.info("Recorded {} consent for {} on purpose {}: {}",
            action, subjectId, purpose, consentId);

        return Promise.of(record);
    }

    @Override
    public Promise<Boolean> verifyConsent(String subjectId, String purpose) {
        List<ConsentRecord> history = subjectHistory.getOrDefault(subjectId, Collections.emptyList());

        // Find the most recent consent for this purpose
        Optional<ConsentRecord> latest = history.stream()
            .filter(r -> r.purpose().equals(purpose))
            .max(Comparator.comparing(ConsentRecord::grantedAt));

        if (latest.isEmpty()) {
            return Promise.of(false);
        }

        ConsentRecord record = latest.get();

        // Check if consent is still valid
        boolean valid = record.status() == ConsentStatus.GRANTED;

        // Check expiration
        if (record.expiresAt() != null && Instant.now().isAfter(record.expiresAt())) {
            valid = false;
        }

        return Promise.of(valid);
    }

    @Override
    public Promise<Void> revokeConsent(String consentId) {
        ConsentRecord existing = consents.get(consentId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException(
                "Consent not found: " + consentId));
        }

        ConsentRecord revoked = new ConsentRecord(
            existing.consentId(),
            existing.subjectId(),
            existing.purpose(),
            ConsentStatus.REVOKED,
            ConsentAction.WITHDRAW,
            existing.legalBasis(),
            existing.grantedAt(),
            existing.expiresAt(),
            Instant.now(),
            existing.metadata()
        );

        consents.put(consentId, revoked);

        LOG.info("Revoked consent {} for subject {}", consentId, existing.subjectId());
        return Promise.complete();
    }

    @Override
    public Promise<List<ConsentRecord>> getConsentHistory(String subjectId) {
        List<ConsentRecord> history = subjectHistory.getOrDefault(subjectId, Collections.emptyList());
        return Promise.of(new ArrayList<>(history));
    }

    @Override
    public Promise<ConsentStatus> getCurrentConsent(String subjectId, String purpose) {
        List<ConsentRecord> history = subjectHistory.getOrDefault(subjectId, Collections.emptyList());

        Optional<ConsentRecord> latest = history.stream()
            .filter(r -> r.purpose().equals(purpose))
            .max(Comparator.comparing(ConsentRecord::grantedAt));

        if (latest.isEmpty()) {
            return Promise.of(ConsentStatus.NOT_REQUESTED);
        }

        ConsentRecord record = latest.get();

        // Check expiration
        if (record.expiresAt() != null && Instant.now().isAfter(record.expiresAt())) {
            return Promise.of(ConsentStatus.EXPIRED);
        }

        return Promise.of(record.status());
    }

    private String determineLegalBasis(String purpose) {
        // Map purposes to legal bases
        if (purpose.contains("healthcare") || purpose.contains("medical")) {
            return "HEALTHCARE_TREATMENT";
        } else if (purpose.contains("financial")) {
            return "CONTRACT_PERFORMANCE";
        } else if (purpose.contains("marketing")) {
            return "CONSENT";
        }
        return "LEGITIMATE_INTEREST";
    }

    private Instant calculateExpiry(String purpose, Instant grantedAt) {
        // Default expiration based on purpose
        long days = switch (purpose) {
            case "marketing" -> 365; // 1 year for marketing
            case "healthcare_data_processing" -> 2555; // 7 years for healthcare
            case "financial_data_sharing" -> 1825; // 5 years for finance
            default -> 730; // 2 years default
        };
        return grantedAt.plusSeconds(days * 24 * 60 * 60);
    }

    private String buildMetadata(String subjectId, String purpose, ConsentAction action) {
        return String.format("{\"subject\":\"%s\",\"purpose\":\"%s\",\"action\":\"%s\"}",
            subjectId, purpose, action);
    }

    @Override
    public String toString() {
        return "StandardConsentPlugin{consents=" + consents.size() +
               ", subjects=" + subjectHistory.size() + "}";
    }
}
