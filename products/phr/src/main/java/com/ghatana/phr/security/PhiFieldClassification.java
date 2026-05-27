package com.ghatana.phr.security;

import java.util.Set;

/**
 * PHI field classification for PHR product.
 *
 * <p>Classifies PHI fields by sensitivity level to determine appropriate
 * security controls, consent requirements, and audit logging requirements.</p>
 *
 * <p>Classification levels:</p>
 * <ul>
 *   <li>IDENTIFIABLE: Direct identifiers (name, ID, DOB) - requires encryption at rest</li>
 *   <li>SENSITIVE: Medical information (diagnosis, medications, lab results) - requires consent</li>
 *   <li>RESTRICTED: Highly sensitive information (mental health, substance use, genetic info, reproductive health, HIV status) - requires explicit consent and additional audit</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose PHI field classification for security controls
 * @doc.layer product
 * @doc.pattern Enum
 * @since 1.0.0
 */
public final class PhiFieldClassification {

    private PhiFieldClassification() {
        // Utility class - prevent instantiation
    }

    public enum SensitivityLevel {
        IDENTIFIABLE,
        SENSITIVE,
        RESTRICTED
    }

    /**
     * Classifies a field name by its sensitivity level.
     *
     * @param fieldName the field name to classify
     * @return the sensitivity level
     */
    public static SensitivityLevel classifyField(String fieldName) {
        if (fieldName == null) {
            return SensitivityLevel.SENSITIVE; // Default to sensitive
        }

        String normalized = fieldName.toLowerCase();

        // Identifiable fields
        if (IDENTIFIABLE_FIELDS.contains(normalized)) {
            return SensitivityLevel.IDENTIFIABLE;
        }

        // Restricted fields
        if (RESTRICTED_FIELDS.contains(normalized)) {
            return SensitivityLevel.RESTRICTED;
        }

        // Default to sensitive for medical fields
        return SensitivityLevel.SENSITIVE;
    }

    /**
     * Checks if a field is restricted (highest sensitivity).
     *
     * @param fieldName the field name to check
     * @return true if the field is restricted
     */
    public static boolean isRestricted(String fieldName) {
        return classifyField(fieldName) == SensitivityLevel.RESTRICTED;
    }

    /**
     * Checks if a field is identifiable.
     *
     * @param fieldName the field name to check
     * @return true if the field is identifiable
     */
    public static boolean isIdentifiable(String fieldName) {
        return classifyField(fieldName) == SensitivityLevel.IDENTIFIABLE;
    }

    /**
     * Checks if a field requires consent for access.
     *
     * @param fieldName the field name to check
     * @return true if the field requires consent
     */
    public static boolean requiresConsent(String fieldName) {
        SensitivityLevel level = classifyField(fieldName);
        return level == SensitivityLevel.SENSITIVE || level == SensitivityLevel.RESTRICTED;
    }

    /**
     * Checks if a field requires additional audit logging.
     *
     * @param fieldName the field name to check
     * @return true if the field requires additional audit logging
     */
    public static boolean requiresEnhancedAudit(String fieldName) {
        return classifyField(fieldName) == SensitivityLevel.RESTRICTED;
    }

    // ==================== Field Lists ====================

    private static final Set<String> IDENTIFIABLE_FIELDS = Set.of(
        "patientid",
        "patient_id",
        "name",
        "firstname",
        "first_name",
        "lastname",
        "last_name",
        "dateofbirth",
        "date_of_birth",
        "dob",
        "nationalid",
        "national_id",
        "address",
        "phonenumber",
        "phone_number",
        "email",
        "ssn",
        "socialsecuritynumber"
    );

    private static final Set<String> RESTRICTED_FIELDS = Set.of(
        "mentalhealth",
        "mental_health",
        "psychiatrichistory",
        "psychiatric_history",
        "substanceuse",
        "substance_use",
        "substanceabuse",
        "substance_abuse",
        "geneticinfo",
        "genetic_info",
        "geneticdata",
        "genetic_data",
        "reproductivehealth",
        "reproductive_health",
        "hivstatus",
        "hiv_status",
        "hiv",
        "stdstatus",
        "std_status",
        "sexualhistory",
        "sexual_history",
        "pregnancy",
        "abortion",
        "contraception"
    );
}
