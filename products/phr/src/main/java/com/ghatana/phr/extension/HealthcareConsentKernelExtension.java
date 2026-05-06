package com.ghatana.phr.extension;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.extension.KernelExtension;
import com.ghatana.kernel.module.KernelModule;
import io.activej.promise.Promise;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Healthcare consent management extension with Nepal Directive 2081 compliance.
 *
 * <p>Implements patient consent management following Nepal Health Service Directives
 * including consent types, expiration, withdrawal, and audit trails as per Directive 2081.</p>
 *
 * @doc.type class
 * @doc.purpose Healthcare consent management extension with Nepal Directive 2081 compliance
 * @doc.layer product
 * @doc.pattern Extension
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class HealthcareConsentKernelExtension implements KernelExtension {

    private static final String EXTENSION_ID = "healthcare-consent-nepal-2081";
    private static final String VERSION = "1.0.0";

    private final ConcurrentHashMap<String, ConsentRecord> consentRegistry = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile KernelContext context;

    @Override
    public String getExtensionId() {
        return EXTENSION_ID;
    }

    @Override
    public String getName() {
        return "Healthcare Consent Management (Nepal Directive 2081)";
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public KernelDescriptor getDescriptor() {
        return new KernelDescriptor.Builder()
            .withDescriptorId(EXTENSION_ID)
            .withName(getName())
            .withVersion(VERSION)
            .withDescription("Healthcare consent management following Nepal Health Service Directive 2081")
            .withType(KernelDescriptor.DescriptorType.EXTENSION)
            .build();
    }

    @Override
    public Set<KernelCapability> getContributedCapabilities() {
        return Set.of(
            // Consent management capability - generic, not product-specific
            new KernelCapability(
                "consent.management",
                "Consent Management",
                "Manages patient consent with regulatory compliance",
                KernelCapability.CapabilityType.SECURITY,
                Map.of(
                    "regulation", "nepal-directive-2081",
                    "privacy_act", "nepal-2075",
                    "supports_withdrawal", "true",
                    "supports_expiration", "true",
                    "audit_required", "true"
                )
            )
        );
    }

    @Override
    public void onModuleInitialized(KernelContext context) {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        this.context = context;
        registerEventHandlers();
    }

    @Override
    public void onModuleStarted(KernelContext context) {
        started.set(true);
    }

    @Override
    public void onModuleStopped(KernelContext context) {
        started.set(false);
    }

    @Override
    public boolean isCompatible(KernelModule hostModule) {
        return hostModule.getCapabilities().stream()
            .anyMatch(c -> c.getCapabilityId().equals("data.storage"));
    }

    @Override
    public int getPriority() {
        return 100; // High priority for consent management
    }

    // Helper methods for lifecycle and context management
    protected final KernelContext getContext() {
        return context;
    }

    protected final boolean isStarted() {
        return started.get();
    }

    protected final void requireStarted() {
        if (!started.get()) {
            throw new IllegalStateException("Extension not started: " + getExtensionId());
        }
    }

    // ==================== Consent Management API ====================

    /**
     * Grants consent for a specific purpose.
     *
     * @param patientId the patient identifier
     * @param purpose the consent purpose
     * @param scope the consent scope (what data is covered)
     * @param duration the consent duration
     * @return Promise containing the consent record
     */
    public Promise<ConsentRecord> grantConsent(String patientId, ConsentPurpose purpose,
                                                ConsentScope scope, ConsentDuration duration) {
        try {
            requireStarted();
        } catch (IllegalStateException exception) {
            return Promise.ofException(exception);
        }

        Instant grantedAt = Instant.now();
        String consentId = generateConsentId(patientId, purpose, grantedAt);
        Instant expiresAt = calculateExpiration(grantedAt, duration);

        ConsentRecord record = new ConsentRecord(
            consentId,
            patientId,
            purpose,
            scope,
            ConsentStatus.GRANTED,
            grantedAt,
            expiresAt,
            null, // withdrawnAt
            generateAuditTrail("GRANTED", patientId, purpose)
        );

        consentRegistry.put(consentId, record);

        // Publish consent granted event
        publishConsentEvent("CONSENT_GRANTED", record);

        return Promise.of(record);
    }

    /**
     * Withdraws previously granted consent.
     *
     * @param consentId the consent identifier
     * @param reason the withdrawal reason
     * @return Promise completing when consent is withdrawn
     */
    public Promise<Void> withdrawConsent(String consentId, String reason) {
        try {
            requireStarted();
        } catch (IllegalStateException exception) {
            return Promise.ofException(exception);
        }

        ConsentRecord existing = consentRegistry.get(consentId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException("Consent not found: " + consentId));
        }

        if (existing.getStatus() != ConsentStatus.GRANTED) {
            return Promise.ofException(new IllegalStateException("Consent is not in GRANTED state"));
        }

        ConsentRecord withdrawn = new ConsentRecord(
            existing.getConsentId(),
            existing.getPatientId(),
            existing.getPurpose(),
            existing.getScope(),
            ConsentStatus.WITHDRAWN,
            existing.getGrantedAt(),
            existing.getExpiresAt(),
            Instant.now(),
            existing.getAuditTrail() + "\n" + generateAuditTrail("WITHDRAWN", existing.getPatientId(), reason)
        );

        consentRegistry.put(consentId, withdrawn);

        publishConsentEvent("CONSENT_WITHDRAWN", withdrawn);

        return Promise.complete();
    }

    /**
     * Verifies if consent is valid for a specific operation.
     *
     * @param patientId the patient identifier
     * @param purpose the intended purpose
     * @param dataType the data type being accessed
     * @return Promise containing verification result
     */
    public Promise<ConsentVerification> verifyConsent(String patientId, ConsentPurpose purpose, String dataType) {
        try {
            requireStarted();
        } catch (IllegalStateException exception) {
            return Promise.ofException(exception);
        }

        ConsentRecord record = findLatestConsent(patientId, purpose).orElse(null);

        if (record == null) {
            return Promise.of(new ConsentVerification(false, "No consent found", null));
        }

        if (record.getStatus() != ConsentStatus.GRANTED) {
            return Promise.of(new ConsentVerification(false, "Consent not in GRANTED state: " + record.getStatus(), record));
        }

        if (record.isExpired()) {
            return Promise.of(new ConsentVerification(false, "Consent has expired", record));
        }

        if (!record.coversDataType(dataType)) {
            return Promise.of(new ConsentVerification(false, "Consent does not cover data type: " + dataType, record));
        }

        return Promise.of(new ConsentVerification(true, "Consent valid", record));
    }

    /**
     * Gets consent history for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing consent history
     */
    public Promise<Set<ConsentRecord>> getConsentHistory(String patientId) {
        try {
            requireStarted();
        } catch (IllegalStateException exception) {
            return Promise.ofException(exception);
        }

        Set<ConsentRecord> records = ConcurrentHashMap.newKeySet();
        consentRegistry.forEach((id, record) -> {
            if (record.getPatientId().equals(patientId)) {
                records.add(record);
            }
        });

        return Promise.of(records);
    }

    // ==================== Private Methods ====================

    private void registerEventHandlers() {
        // Register event handlers for consent-related events
    }

    private Optional<ConsentRecord> findLatestConsent(String patientId, ConsentPurpose purpose) {
        return consentRegistry.values().stream()
            .filter(r -> r.getPatientId().equals(patientId) && r.getPurpose() == purpose)
            .max(java.util.Comparator.comparing(ConsentRecord::getGrantedAt));
    }

    private String generateConsentId(String patientId, ConsentPurpose purpose, Instant grantedAt) {
        String canonical = patientId + ":" + purpose.name() + ":" + grantedAt.toString();
        String digest = Integer.toHexString(canonical.hashCode());
        return patientId + ":" + purpose.name() + ":" + digest;
    }

    private Instant calculateExpiration(Instant grantedAt, ConsentDuration duration) {
        LocalDate grantedDate = LocalDate.ofInstant(grantedAt, java.time.ZoneId.systemDefault());
        LocalDate expiryDate = switch (duration) {
            case ONE_TIME -> grantedDate;
            case THIRTY_DAYS -> grantedDate.plusDays(30);
            case NINETY_DAYS -> grantedDate.plusDays(90);
            case ONE_YEAR -> grantedDate.plusYears(1);
            case UNTIL_WITHDRAWN -> grantedDate.plusYears(100); // Effectively permanent
        };
        return expiryDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
    }

    private String generateAuditTrail(String action, String actor, Object details) {
        return String.format("[%s] %s by %s: %s",
            Instant.now(), action, actor, details);
    }

    private void publishConsentEvent(String eventType, ConsentRecord record) {
        // Publish event to kernel event system
    }

    // ==================== Inner Types ====================

    public enum ConsentPurpose {
        TREATMENT,
        RESEARCH,
        QUALITY_IMPROVEMENT,
        BILLING,
        PUBLIC_HEALTH,
        MARKETING // Explicitly requires separate consent per Nepal Directive 2081
    }

    public enum ConsentScope {
        ALL_DATA,           // All health records
        SPECIFIC_RECORDS,   // Specific record types
        ANONYMIZED_ONLY,    // Only anonymized data
        SUMMARY_ONLY        // Summary data only
    }

    public enum ConsentDuration {
        ONE_TIME,           // Single use
        THIRTY_DAYS,        // 30 days
        NINETY_DAYS,        // 90 days
        ONE_YEAR,           // 1 year
        UNTIL_WITHDRAWN     // Until explicitly withdrawn
    }

    public enum ConsentStatus {
        GRANTED,
        WITHDRAWN,
        EXPIRED,
        SUSPENDED
    }

    public static class ConsentRecord {
        private final String consentId;
        private final String patientId;
        private final ConsentPurpose purpose;
        private final ConsentScope scope;
        private final ConsentStatus status;
        private final Instant grantedAt;
        private final Instant expiresAt;
        private final Instant withdrawnAt;
        private final String auditTrail;

        public ConsentRecord(String consentId, String patientId, ConsentPurpose purpose,
                            ConsentScope scope, ConsentStatus status, Instant grantedAt,
                            Instant expiresAt, Instant withdrawnAt, String auditTrail) {
            this.consentId = consentId;
            this.patientId = patientId;
            this.purpose = purpose;
            this.scope = scope;
            this.status = status;
            this.grantedAt = grantedAt;
            this.expiresAt = expiresAt;
            this.withdrawnAt = withdrawnAt;
            this.auditTrail = auditTrail;
        }

        public String getConsentId() { return consentId; }
        public String getPatientId() { return patientId; }
        public ConsentPurpose getPurpose() { return purpose; }
        public ConsentScope getScope() { return scope; }
        public ConsentStatus getStatus() { return status; }
        public Instant getGrantedAt() { return grantedAt; }
        public Instant getExpiresAt() { return expiresAt; }
        public Instant getWithdrawnAt() { return withdrawnAt; }
        public String getAuditTrail() { return auditTrail; }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        public boolean coversDataType(String dataType) {
            return switch (scope) {
                case ALL_DATA -> true;
                case SPECIFIC_RECORDS -> dataType.equals("EMR") || dataType.equals("LAB");
                case ANONYMIZED_ONLY -> dataType.equals("ANONYMIZED");
                case SUMMARY_ONLY -> dataType.equals("SUMMARY");
            };
        }
    }

    public static class ConsentVerification {
        private final boolean valid;
        private final String reason;
        private final ConsentRecord record;

        public ConsentVerification(boolean valid, String reason, ConsentRecord record) {
            this.valid = valid;
            this.reason = reason;
            this.record = record;
        }

        public boolean isValid() { return valid; }
        public String getReason() { return reason; }
        public Optional<ConsentRecord> getRecord() { return Optional.ofNullable(record); }
    }
}
