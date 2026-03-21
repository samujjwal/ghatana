package com.ghatana.phr.kernel.policy;

import com.ghatana.kernel.policy.ClassificationDescriptor.SensitivityLevel;

/**
 * PHR data classification taxonomy aligned with Nepal Directive 2081.
 *
 * <p>PHR uses a C1-C4 classification scale for health data, which maps to the
 * kernel's four-level {@link SensitivityLevel} taxonomy. This enum is the
 * single point of truth for all PHR tiered data-protection decisions:
 * encryption, audit verbosity, retention floor, and consent requirements.</p>
 *
 * <h3>PHR Classification Tiers</h3>
 * <ul>
 *   <li><b>C1</b> — Administrative / Non-sensitive (maps to PUBLIC)</li>
 *   <li><b>C2</b> — Operational / Low-sensitivity (maps to INTERNAL)</li>
 *   <li><b>C3</b> — Health data / Sensitive (maps to CONFIDENTIAL)</li>
 *   <li><b>C4</b> — Highly sensitive health data (maps to RESTRICTED)</li>
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose PHR data classification with kernel SensitivityLevel bridge
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum PhrDataClassification {

    /**
     * C1 — Administrative data with no patient linkage.
     * Examples: system config, feature flags, audit metadata, provider listings.
     * Encryption: standard at-rest. Retention: standard operational.
     */
    C1("Administrative", SensitivityLevel.PUBLIC, false, false),

    /**
     * C2 — Operational data with indirect patient linkage.
     * Examples: appointment slots, facility capacity, provider schedules.
     * Encryption: standard at-rest + TLS in-transit. Retention: 3 years.
     */
    C2("Operational", SensitivityLevel.INTERNAL, false, false),

    /**
     * C3 — Sensitive clinical and personal health data.
     * Examples: Patient, Encounter, Observation, Condition, AllergyIntolerance,
     * MedicationRequest, Procedure, CarePlan, DiagnosticReport, ImagingStudy.
     * Encryption: AES-256 at-rest + field-level for PII. Retention: 7 years minimum.
     * Consent gate required.
     */
    C3("Sensitive", SensitivityLevel.CONFIDENTIAL, true, false),

    /**
     * C4 — Highly sensitive restricted health data.
     * Examples: Coverage, Claim, ClaimResponse, mental health notes, HIV/STI
     * records, reproductive health, substance use disorder records.
     * Encryption: AES-256 at-rest + field-level encryption on all sensitive columns.
     * Retention: Nepal Directive 2081 (25 years). Explicit secondary consent required.
     */
    C4("Restricted", SensitivityLevel.RESTRICTED, true, true);

    private final String label;
    private final SensitivityLevel kernelLevel;
    private final boolean requiresConsent;
    private final boolean requiresSecondaryConsent;

    PhrDataClassification(
            String label,
            SensitivityLevel kernelLevel,
            boolean requiresConsent,
            boolean requiresSecondaryConsent) {
        this.label = label;
        this.kernelLevel = kernelLevel;
        this.requiresConsent = requiresConsent;
        this.requiresSecondaryConsent = requiresSecondaryConsent;
    }

    /** Human-readable tier label used in DPIA and audit reports. */
    public String getLabel() { return label; }

    /** Kernel {@link SensitivityLevel} this PHR tier maps to. */
    public SensitivityLevel toKernelLevel() { return kernelLevel; }

    /** Whether this classification requires an active consent grant for access. */
    public boolean requiresConsent() { return requiresConsent; }

    /**
     * Whether access requires secondary (break-the-glass) policy approval.
     * Applies to C4 restricted categories such as mental health, HIV, and
     * reproductive health records per Nepal Directive 2081.
     */
    public boolean requiresSecondaryConsent() { return requiresSecondaryConsent; }

    /**
     * Resolves the PHR classification from its string representation used in
     * consent check requests and event payloads.
     *
     * @param value "C1", "C2", "C3", or "C4"
     * @return the matching classification
     * @throws IllegalArgumentException if the string is not a recognised tier
     */
    public static PhrDataClassification fromString(String value) {
        return switch (value) {
            case "C1" -> C1;
            case "C2" -> C2;
            case "C3" -> C3;
            case "C4" -> C4;
            default -> throw new IllegalArgumentException(
                    "Unknown PHR classification tier: '" + value + "'. Expected C1-C4.");
        };
    }

    /**
     * Maps a kernel {@link SensitivityLevel} to the closest PHR classification tier.
     *
     * @param level the kernel sensitivity level
     * @return the corresponding PHR tier
     */
    public static PhrDataClassification fromKernelLevel(SensitivityLevel level) {
        return switch (level) {
            case PUBLIC -> C1;
            case INTERNAL -> C2;
            case CONFIDENTIAL -> C3;
            case RESTRICTED -> C4;
        };
    }

    @Override
    public String toString() { return name() + "(" + label + ")"; }
}
