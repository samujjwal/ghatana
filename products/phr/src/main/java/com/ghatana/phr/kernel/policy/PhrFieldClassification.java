package com.ghatana.phr.kernel.policy;


/**
 * Canonical PHI/PII field classification for PHR Nepal.
 *
 * Defines sensitivity levels for health data fields per Nepal Privacy Act 2075
 * and HIPAA standards. Used for log redaction, audit export filtering, and
 * notification content sanitization.
 *
 * @doc.type class
 * @doc.purpose Canonical field classification for PHI/PII data handling
 * @doc.layer product
 * @doc.pattern Enumeration, Policy
 */
public final class PhrFieldClassification {

    private PhrFieldClassification() {}

    /**
     * Sensitivity levels for PHI/PII fields.
     */
    public enum SensitivityLevel {
        /** Public information that can be safely logged and displayed. */
        PUBLIC,

        /** Identifiable information requiring redaction in logs but may appear in audit exports. */
        IDENTIFIABLE,

        /** Sensitive health information requiring strict redaction in logs and limited audit export. */
        SENSITIVE_PHI,

        /** Highly sensitive information requiring maximum protection. */
        RESTRICTED
    }

    /**
     * Canonical field classifications.
     */
    public static final class Field {
        private final String fieldName;
        private final SensitivityLevel sensitivity;
        private final String description;

        public Field(String fieldName, SensitivityLevel sensitivity, String description) {
            this.fieldName = fieldName;
            this.sensitivity = sensitivity;
            this.description = description;
        }

        public String fieldName() { return fieldName; }
        public SensitivityLevel sensitivity() { return sensitivity; }
        public String description() { return description; }
    }

    // Patient identity fields
    public static final Field PATIENT_ID = new Field("patientId", SensitivityLevel.IDENTIFIABLE, "Unique patient identifier");
    public static final Field NATIONAL_ID = new Field("nationalId", SensitivityLevel.RESTRICTED, "National ID number");
    public static final Field FULL_NAME = new Field("fullName", SensitivityLevel.IDENTIFIABLE, "Patient full name");
    public static final Field DATE_OF_BIRTH = new Field("dateOfBirth", SensitivityLevel.IDENTIFIABLE, "Patient date of birth");
    public static final Field PHONE_NUMBER = new Field("phoneNumber", SensitivityLevel.IDENTIFIABLE, "Contact phone number");
    public static final Field EMAIL = new Field("email", SensitivityLevel.IDENTIFIABLE, "Contact email address");
    public static final Field ADDRESS = new Field("address", SensitivityLevel.IDENTIFIABLE, "Physical address");

    // Medical record fields
    public static final Field DIAGNOSIS = new Field("diagnosis", SensitivityLevel.SENSITIVE_PHI, "Medical diagnosis codes and descriptions");
    public static final Field MEDICATION = new Field("medication", SensitivityLevel.SENSITIVE_PHI, "Medication names and dosages");
    public static final Field LAB_RESULTS = new Field("labResults", SensitivityLevel.SENSITIVE_PHI, "Laboratory test results");
    public static final Field VITAL_SIGNS = new Field("vitalSigns", SensitivityLevel.SENSITIVE_PHI, "Vital signs measurements");
    public static final Field PROCEDURE = new Field("procedure", SensitivityLevel.SENSITIVE_PHI, "Medical procedures performed");
    public static final Field ALLERGY = new Field("allergy", SensitivityLevel.SENSITIVE_PHI, "Known allergies and reactions");
    public static final Field CONDITION = new Field("condition", SensitivityLevel.SENSITIVE_PHI, "Health conditions and status");

    // Highly sensitive fields
    public static final Field MENTAL_HEALTH = new Field("mentalHealth", SensitivityLevel.RESTRICTED, "Mental health records");
    public static final Field SUBSTANCE_USE = new Field("substanceUse", SensitivityLevel.RESTRICTED, "Substance use history");
    public static final Field GENETIC_INFO = new Field("geneticInfo", SensitivityLevel.RESTRICTED, "Genetic test results");
    public static final Field REPRODUCTIVE_HEALTH = new Field("reproductiveHealth", SensitivityLevel.RESTRICTED, "Reproductive health information");
    public static final Field HIV_STATUS = new Field("hivStatus", SensitivityLevel.RESTRICTED, "HIV/AIDS status");
    public static final Field SOCIAL_SECURITY = new Field("socialSecurity", SensitivityLevel.RESTRICTED, "Social security number");

    // Public/administrative fields
    public static final Field RECORD_ID = new Field("recordId", SensitivityLevel.PUBLIC, "Internal record identifier");
    public static final Field RECORD_TYPE = new Field("recordType", SensitivityLevel.PUBLIC, "Type of medical record");
    public static final Field FACILITY_ID = new Field("facilityId", SensitivityLevel.PUBLIC, "Healthcare facility identifier");
    public static final Field PROVIDER_ID = new Field("providerId", SensitivityLevel.IDENTIFIABLE, "Healthcare provider identifier");
    public static final Field TIMESTAMP = new Field("timestamp", SensitivityLevel.PUBLIC, "Event timestamp");
    public static final Field STATUS = new Field("status", SensitivityLevel.PUBLIC, "Record status (active/inactive)");

    /**
     * Returns the sensitivity level for a given field name.
     *
     * @param fieldName the field name to classify
     * @return the sensitivity level, or SENSITIVE_PHI if unknown (fail closed)
     */
    public static SensitivityLevel classifyField(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return SensitivityLevel.SENSITIVE_PHI;
        }

        return switch (fieldName.toLowerCase()) {
            case "patientid", "patient_id" -> PATIENT_ID.sensitivity();
            case "nationalid", "national_id" -> NATIONAL_ID.sensitivity();
            case "fullname", "full_name", "name" -> FULL_NAME.sensitivity();
            case "dateofbirth", "date_of_birth", "dob" -> DATE_OF_BIRTH.sensitivity();
            case "phonenumber", "phone_number", "phone" -> PHONE_NUMBER.sensitivity();
            case "email", "emailaddress" -> EMAIL.sensitivity();
            case "address" -> ADDRESS.sensitivity();
            case "diagnosis", "diagnoses" -> DIAGNOSIS.sensitivity();
            case "medication", "medications" -> MEDICATION.sensitivity();
            case "labresults", "lab_results", "lab" -> LAB_RESULTS.sensitivity();
            case "vitalsigns", "vital_signs", "vitals" -> VITAL_SIGNS.sensitivity();
            case "procedure", "procedures" -> PROCEDURE.sensitivity();
            case "allergy", "allergies" -> ALLERGY.sensitivity();
            case "condition", "conditions" -> CONDITION.sensitivity();
            case "recordid", "record_id", "id" -> RECORD_ID.sensitivity();
            case "recordtype", "record_type", "type" -> RECORD_TYPE.sensitivity();
            case "facilityid", "facility_id" -> FACILITY_ID.sensitivity();
            case "providerid", "provider_id" -> PROVIDER_ID.sensitivity();
            case "timestamp", "createdat", "updatedat" -> TIMESTAMP.sensitivity();
            case "status" -> STATUS.sensitivity();
            default -> SensitivityLevel.SENSITIVE_PHI; // Fail closed for unknown fields
        };
    }

    /**
     * Returns whether a field should be redacted in logs.
     *
     * @param fieldName the field name to check
     * @return true if the field should be redacted in logs
     */
    public static boolean shouldRedactInLogs(String fieldName) {
        SensitivityLevel level = classifyField(fieldName);
        return level != SensitivityLevel.PUBLIC;
    }

    /**
     * Returns whether a field should be redacted in audit exports.
     *
     * @param fieldName the field name to check
     * @return true if the field should be redacted in audit exports
     */
    public static boolean shouldRedactInAuditExport(String fieldName) {
        SensitivityLevel level = classifyField(fieldName);
        return level == SensitivityLevel.RESTRICTED;
    }

    /**
     * Returns whether a field should be redacted in notifications.
     *
     * @param fieldName the field name to check
     * @return true if the field should be redacted in notifications
     */
    public static boolean shouldRedactInNotifications(String fieldName) {
        SensitivityLevel level = classifyField(fieldName);
        return level != SensitivityLevel.PUBLIC;
    }

    /**
     * Returns whether a field should be redacted in mobile cache.
     *
     * @param fieldName the field name to check
     * @return true if the field should be redacted in mobile cache
     */
    public static boolean shouldRedactInMobileCache(String fieldName) {
        SensitivityLevel level = classifyField(fieldName);
        return level == SensitivityLevel.RESTRICTED;
    }

    /**
     * Redacts a field value based on its sensitivity level.
     *
     * @param fieldName the field name
     * @param value the field value
     * @return the redacted value, or the original if not sensitive
     */
    public static String redactValue(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        SensitivityLevel level = classifyField(fieldName);
        if (level == SensitivityLevel.PUBLIC) {
            return value;
        }

        return switch (level) {
            case IDENTIFIABLE -> "[REDACTED-ID]";
            case SENSITIVE_PHI -> "[REDACTED-PHI]";
            case RESTRICTED -> "[REDACTED-RESTRICTED]";
            default -> value;
        };
    }
}
