/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.pac.library;

import com.ghatana.platform.pac.InMemoryPolicyEngine;
import com.ghatana.platform.pac.PolicyEvalResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Tests for NepalHealthcareRuleLibrary
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("NepalHealthcareRuleLibrary [GH-90000]")
class NepalHealthcareRuleLibraryTest extends EventloopTestBase {

    private InMemoryPolicyEngine engine;
    private final NepalHealthcareRuleLibrary library = new NepalHealthcareRuleLibrary(); // GH-90000

    @BeforeEach
    void setUp() { // GH-90000
        engine = new InMemoryPolicyEngine(); // GH-90000
        library.registerInto(engine); // GH-90000
    }

    // -------------------------------------------------------------------------
    // metadata
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("id and version are populated [GH-90000]")
    void metadata() { // GH-90000
        assertThat(library.id()).isEqualTo("nepal-healthcare-directive-2081 [GH-90000]");
        assertThat(library.version()).isEqualTo("1.0.0 [GH-90000]");
        assertThat(library.description()).isNotBlank(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // patient_data_access
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("patient_data_access [GH-90000]")
    class DataAccess {

        @Test
        @DisplayName("DOCTOR with consent allowed on HIGH sensitivity data [GH-90000]")
        void doctor_with_consent_allowed() { // GH-90000
            PolicyEvalResult result = evaluate("nepal_healthcare.patient_data_access", // GH-90000
                    Map.of("role", "DOCTOR", "consent_on_file", true, "data_sensitivity", "HIGH")); // GH-90000
            assertThat(result.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("NURSE without consent denied on HIGH sensitivity data [GH-90000]")
        void nurse_without_consent_denied_high() { // GH-90000
            PolicyEvalResult result = evaluate("nepal_healthcare.patient_data_access", // GH-90000
                    Map.of("role", "NURSE", "consent_on_file", false, "data_sensitivity", "HIGH")); // GH-90000
            assertThat(result.allowed()).isFalse(); // GH-90000
            assertThat(result.reasons()).anyMatch(r -> r.contains("consent [GH-90000]"));
        }

        @Test
        @DisplayName("ADMIN role (not care role) is denied [GH-90000]")
        void non_care_role_denied() { // GH-90000
            PolicyEvalResult result = evaluate("nepal_healthcare.patient_data_access", // GH-90000
                    Map.of("role", "ADMIN", "consent_on_file", true, "data_sensitivity", "NORMAL")); // GH-90000
            assertThat(result.allowed()).isFalse(); // GH-90000
            assertThat(result.reasons()).anyMatch(r -> r.contains("ADMIN [GH-90000]"));
        }

        @Test
        @DisplayName("NURSE with consent on NORMAL sensitivity is allowed [GH-90000]")
        void nurse_with_consent_normal_allowed() { // GH-90000
            PolicyEvalResult result = evaluate("nepal_healthcare.patient_data_access", // GH-90000
                    Map.of("role", "NURSE", "consent_on_file", true, "data_sensitivity", "NORMAL")); // GH-90000
            assertThat(result.allowed()).isTrue(); // GH-90000
        }
    }

    // -------------------------------------------------------------------------
    // patient_data_export
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("patient_data_export [GH-90000]")
    class DataExport {

        @Test
        @DisplayName("DOCTOR with consent and purpose is allowed to export [GH-90000]")
        void doctor_with_consent_and_purpose_allowed() { // GH-90000
            PolicyEvalResult result = evaluate("nepal_healthcare.patient_data_export", // GH-90000
                    Map.of("role", "DOCTOR", "consent_on_file", true, "purpose", "referral")); // GH-90000
            assertThat(result.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("NURSE (not in export list) is denied [GH-90000]")
        void nurse_denied_export() { // GH-90000
            PolicyEvalResult result = evaluate("nepal_healthcare.patient_data_export", // GH-90000
                    Map.of("role", "NURSE", "consent_on_file", true, "purpose", "referral")); // GH-90000
            assertThat(result.allowed()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("PHR_ADMIN without consent is denied [GH-90000]")
        void admin_without_consent_denied() { // GH-90000
            PolicyEvalResult result = evaluate("nepal_healthcare.patient_data_export", // GH-90000
                    Map.of("role", "PHR_ADMIN", "consent_on_file", false, "purpose", "audit")); // GH-90000
            assertThat(result.allowed()).isFalse(); // GH-90000
            assertThat(result.reasons()).anyMatch(r -> r.contains("consent [GH-90000]"));
        }

        @Test
        @DisplayName("DOCTOR with consent but blank purpose is denied [GH-90000]")
        void doctor_blank_purpose_denied() { // GH-90000
            PolicyEvalResult result = evaluate("nepal_healthcare.patient_data_export", // GH-90000
                    Map.of("role", "DOCTOR", "consent_on_file", true, "purpose", "  ")); // GH-90000
            assertThat(result.allowed()).isFalse(); // GH-90000
            assertThat(result.reasons()).anyMatch(r -> r.contains("purpose [GH-90000]"));
        }
    }

    // -------------------------------------------------------------------------
    // retention_check
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("retention_check [GH-90000]")
    class Retention {

        @Test
        @DisplayName("Record older than 7 years passes retention check [GH-90000]")
        void old_record_passes() { // GH-90000
            PolicyEvalResult result = evaluate("nepal_healthcare.retention_check", // GH-90000
                    Map.of("record_age_days", 7 * 365 + 1)); // GH-90000
            assertThat(result.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Record younger than 7 years fails retention check [GH-90000]")
        void young_record_fails() { // GH-90000
            PolicyEvalResult result = evaluate("nepal_healthcare.retention_check", // GH-90000
                    Map.of("record_age_days", 365)); // GH-90000
            assertThat(result.allowed()).isFalse(); // GH-90000
            assertThat(result.reasons()).anyMatch(r -> r.contains("2555 [GH-90000]"));
        }
    }

    // -------------------------------------------------------------------------
    // consent_required
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Operation with consent on file is allowed [GH-90000]")
    void consent_present_allowed() { // GH-90000
        PolicyEvalResult result = evaluate("nepal_healthcare.consent_required", // GH-90000
                Map.of("operation", "blood_test", "consent_on_file", true)); // GH-90000
        assertThat(result.allowed()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Operation without consent is denied [GH-90000]")
    void consent_absent_denied() { // GH-90000
        PolicyEvalResult result = evaluate("nepal_healthcare.consent_required", // GH-90000
                Map.of("operation", "blood_test", "consent_on_file", false)); // GH-90000
        assertThat(result.allowed()).isFalse(); // GH-90000
        assertThat(result.reasons()).anyMatch(r -> r.contains("blood_test [GH-90000]"));
    }

    // -------------------------------------------------------------------------
    // emergency_override
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("emergency_override [GH-90000]")
    class EmergencyOverride {

        @Test
        @DisplayName("Valid emergency code + physician is allowed [GH-90000]")
        void valid_emergency_allowed() { // GH-90000
            PolicyEvalResult result = evaluate("nepal_healthcare.emergency_override", // GH-90000
                    Map.of("emergency_code", "EMERGENCY-2081", // GH-90000
                           "authorizing_physician", "dr-sharma"));
            assertThat(result.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Wrong emergency code is denied [GH-90000]")
        void wrong_code_denied() { // GH-90000
            PolicyEvalResult result = evaluate("nepal_healthcare.emergency_override", // GH-90000
                    Map.of("emergency_code", "WRONG-CODE", // GH-90000
                           "authorizing_physician", "dr-sharma"));
            assertThat(result.allowed()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Missing physician ID is denied even with correct code [GH-90000]")
        void missing_physician_denied() { // GH-90000
            PolicyEvalResult result = evaluate("nepal_healthcare.emergency_override", // GH-90000
                    Map.of("emergency_code", "EMERGENCY-2081", // GH-90000
                           "authorizing_physician", ""));
            assertThat(result.allowed()).isFalse(); // GH-90000
        }
    }

    // -------------------------------------------------------------------------
    // overlay / override semantics
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Later registerInto() call overrides earlier one for same policy name [GH-90000]")
    void registerInto_overrides_earlier_registration() { // GH-90000
        // Register a permissive override for consent_required
        engine.register("nepal_healthcare.consent_required", // GH-90000
                input -> PolicyEvalResult.allow("nepal_healthcare.consent_required [GH-90000]"));

        PolicyEvalResult result = evaluate("nepal_healthcare.consent_required", // GH-90000
                Map.of("operation", "risky_op", "consent_on_file", false)); // GH-90000

        // Override wins — allowed even without consent
        assertThat(result.allowed()).isTrue(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private PolicyEvalResult evaluate(String policy, Map<String, Object> input) { // GH-90000
        return runPromise(() -> engine.evaluate("tenant-nep", policy, input)); // GH-90000
    }
}
