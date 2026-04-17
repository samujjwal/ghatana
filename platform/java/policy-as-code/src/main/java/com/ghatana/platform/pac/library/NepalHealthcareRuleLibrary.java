package com.ghatana.platform.pac.library;

import com.ghatana.platform.pac.InMemoryPolicyEngine;
import com.ghatana.platform.pac.PolicyEvalResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rule library for Nepal Health Data Directive 2081 (BS).
 *
 * <p>Implements the consent, data-access, data-retention, and audit requirements
 * defined by Nepal's healthcare data governance directive. The rules are written
 * against the {@link InMemoryPolicyEngine} for dev/test; in production environments
 * these same policy names should be backed by OPA documents.</p>
 *
 * <h3>Governed policy names</h3>
 * <ul>
 *   <li>{@code nepal_healthcare.patient_data_access}</li>
 *   <li>{@code nepal_healthcare.patient_data_export}</li>
 *   <li>{@code nepal_healthcare.retention_check}</li>
 *   <li>{@code nepal_healthcare.consent_required}</li>
 *   <li>{@code nepal_healthcare.emergency_override}</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Nepal Directive 2081 regulated rule library
 * @doc.layer platform
 * @doc.pattern RuleLibrary
 * @since 1.0.0
 */
public final class NepalHealthcareRuleLibrary implements RuleLibrary {

    /** Roles permitted to access patient data under Directive 2081. */
    private static final List<String> ALLOWED_CARE_ROLES =
            List.of("DOCTOR", "NURSE", "PHARMACIST", "LAB_TECHNICIAN", "RADIOLOGIST");

    /** Roles permitted to export patient data outside the facility. */
    private static final List<String> EXPORT_ALLOWED_ROLES =
            List.of("DOCTOR", "PHR_ADMIN");

    /** Minimum retention period (days) for patient records under Directive 2081. */
    private static final int MIN_RETENTION_DAYS = 7 * 365;   // 7 years

    @Override
    public String id() {
        return "nepal-healthcare-directive-2081";
    }

    @Override
    public String version() {
        return "1.0.0";
    }

    @Override
    public String description() {
        return "Nepal Health Data Directive 2081 (BS) - patient data access, export, "
                + "retention, consent, and emergency override rules";
    }

    @Override
    public void registerInto(InMemoryPolicyEngine engine) {
        engine.register("nepal_healthcare.patient_data_access",  this::evaluateDataAccess);
        engine.register("nepal_healthcare.patient_data_export",  this::evaluateDataExport);
        engine.register("nepal_healthcare.retention_check",      this::evaluateRetention);
        engine.register("nepal_healthcare.consent_required",     this::evaluateConsentRequired);
        engine.register("nepal_healthcare.emergency_override",   this::evaluateEmergencyOverride);
    }

    // -------------------------------------------------------------------------
    // Rule: patient_data_access
    // Input keys: role (String), consent_on_file (Boolean), data_sensitivity (String HIGH|NORMAL)
    // -------------------------------------------------------------------------

    private PolicyEvalResult evaluateDataAccess(Map<String, Object> input) {
        String role = asString(input, "role");
        boolean consentOnFile = asBoolean(input, "consent_on_file");
        String sensitivity = asString(input, "data_sensitivity");

        List<String> violations = new ArrayList<>();

        if (!ALLOWED_CARE_ROLES.contains(role)) {
            violations.add("Role '" + role + "' is not a permitted care role under Directive 2081");
        }

        if ("HIGH".equalsIgnoreCase(sensitivity) && !consentOnFile) {
            violations.add("High-sensitivity patient data requires explicit consent on file");
        }

        if (violations.isEmpty()) {
            return PolicyEvalResult.allow("nepal_healthcare.patient_data_access");
        }
        return PolicyEvalResult.deny("nepal_healthcare.patient_data_access", violations, 70);
    }

    // -------------------------------------------------------------------------
    // Rule: patient_data_export
    // Input keys: role (String), purpose (String), consent_on_file (Boolean)
    // -------------------------------------------------------------------------

    private PolicyEvalResult evaluateDataExport(Map<String, Object> input) {
        String role = asString(input, "role");
        String purpose = asString(input, "purpose");
        boolean consentOnFile = asBoolean(input, "consent_on_file");

        List<String> violations = new ArrayList<>();

        if (!EXPORT_ALLOWED_ROLES.contains(role)) {
            violations.add("Role '" + role + "' is not authorized to export patient data");
        }

        if (!consentOnFile) {
            violations.add("Patient data export requires written consent under Directive 2081 §14");
        }

        if (purpose == null || purpose.isBlank()) {
            violations.add("Export purpose must be documented per Directive 2081 §15");
        }

        if (violations.isEmpty()) {
            return PolicyEvalResult.allow("nepal_healthcare.patient_data_export");
        }
        return PolicyEvalResult.deny("nepal_healthcare.patient_data_export", violations, 85);
    }

    // -------------------------------------------------------------------------
    // Rule: retention_check
    // Input keys: record_age_days (Number)
    // -------------------------------------------------------------------------

    private PolicyEvalResult evaluateRetention(Map<String, Object> input) {
        int ageDays = asInt(input, "record_age_days");

        if (ageDays < MIN_RETENTION_DAYS) {
            return PolicyEvalResult.deny(
                    "nepal_healthcare.retention_check",
                    List.of("Record must be retained for at least " + MIN_RETENTION_DAYS + " days "
                            + "(Directive 2081 §22); current age: " + ageDays + " days"),
                    60);
        }
        return PolicyEvalResult.allow("nepal_healthcare.retention_check");
    }

    // -------------------------------------------------------------------------
    // Rule: consent_required
    // Input keys: operation (String), consent_on_file (Boolean)
    // -------------------------------------------------------------------------

    private PolicyEvalResult evaluateConsentRequired(Map<String, Object> input) {
        boolean consentOnFile = asBoolean(input, "consent_on_file");

        if (!consentOnFile) {
            String operation = asString(input, "operation");
            return PolicyEvalResult.deny(
                    "nepal_healthcare.consent_required",
                    List.of("Operation '" + operation + "' requires prior consent under Directive 2081"),
                    75);
        }
        return PolicyEvalResult.allow("nepal_healthcare.consent_required");
    }

    // -------------------------------------------------------------------------
    // Rule: emergency_override
    // Input keys: emergency_code (String), authorizing_physician (String)
    // -------------------------------------------------------------------------

    private PolicyEvalResult evaluateEmergencyOverride(Map<String, Object> input) {
        String code = asString(input, "emergency_code");
        String physician = asString(input, "authorizing_physician");

        // A valid emergency bypass requires a non-blank physician ID and the
        // standard emergency code issued by the facility authority.
        if ("EMERGENCY-2081".equals(code) && physician != null && !physician.isBlank()) {
            return PolicyEvalResult.allow("nepal_healthcare.emergency_override");
        }

        return PolicyEvalResult.deny(
                "nepal_healthcare.emergency_override",
                List.of("Emergency override requires a valid emergency code and authorizing physician ID"),
                50);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String asString(Map<String, Object> input, String key) {
        Object v = input.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static boolean asBoolean(Map<String, Object> input, String key) {
        Object v = input.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }

    private static int asInt(Map<String, Object> input, String key) {
        Object v = input.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) { }
        }
        return 0;
    }
}
