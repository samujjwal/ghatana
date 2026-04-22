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
 * @doc.purpose Tests for FinanceSoxRuleLibrary
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("FinanceSoxRuleLibrary [GH-90000]")
class FinanceSoxRuleLibraryTest extends EventloopTestBase {

    private InMemoryPolicyEngine engine;
    private final FinanceSoxRuleLibrary library = new FinanceSoxRuleLibrary(); // GH-90000

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
        assertThat(library.id()).isEqualTo("sox-financial-controls [GH-90000]");
        assertThat(library.version()).isEqualTo("1.0.0 [GH-90000]");
        assertThat(library.description()).isNotBlank(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // financial_record_access
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("financial_record_access [GH-90000]")
    class RecordAccess {

        @Test
        @DisplayName("AUDITOR accessing RESTRICTED records is allowed [GH-90000]")
        void auditor_restricted_allowed() { // GH-90000
            PolicyEvalResult result = evaluate("sox.financial_record_access", // GH-90000
                    Map.of("role", "AUDITOR", "record_classification", "RESTRICTED")); // GH-90000
            assertThat(result.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("FINANCE_ANALYST accessing RESTRICTED records is denied [GH-90000]")
        void analyst_restricted_denied() { // GH-90000
            PolicyEvalResult result = evaluate("sox.financial_record_access", // GH-90000
                    Map.of("role", "FINANCE_ANALYST", "record_classification", "RESTRICTED")); // GH-90000
            assertThat(result.allowed()).isFalse(); // GH-90000
            assertThat(result.reasons()).anyMatch(r -> r.contains("RESTRICTED [GH-90000]"));
        }

        @Test
        @DisplayName("ENGINEER role (not finance) is denied [GH-90000]")
        void engineer_denied() { // GH-90000
            PolicyEvalResult result = evaluate("sox.financial_record_access", // GH-90000
                    Map.of("role", "ENGINEER", "record_classification", "CONFIDENTIAL")); // GH-90000
            assertThat(result.allowed()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("CFO accessing any record is allowed [GH-90000]")
        void cfo_allowed() { // GH-90000
            PolicyEvalResult result = evaluate("sox.financial_record_access", // GH-90000
                    Map.of("role", "CFO", "record_classification", "RESTRICTED")); // GH-90000
            assertThat(result.allowed()).isTrue(); // GH-90000
        }
    }

    // -------------------------------------------------------------------------
    // transaction_approval
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("transaction_approval [GH-90000]")
    class TransactionApproval {

        @Test
        @DisplayName("CFO approving a transaction by another user is allowed [GH-90000]")
        void cfo_approves_different_user() { // GH-90000
            PolicyEvalResult result = evaluate("sox.transaction_approval", // GH-90000
                    Map.of("approver_role", "CFO", // GH-90000
                           "requester_id", "analyst-1",
                           "approver_id",  "cfo-jane"));
            assertThat(result.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Self-approval is denied regardless of role [GH-90000]")
        void self_approval_denied() { // GH-90000
            PolicyEvalResult result = evaluate("sox.transaction_approval", // GH-90000
                    Map.of("approver_role", "CFO", // GH-90000
                           "requester_id", "cfo-jane",
                           "approver_id",  "cfo-jane"));
            assertThat(result.allowed()).isFalse(); // GH-90000
            assertThat(result.reasons()).anyMatch(r -> r.contains("Self-approval [GH-90000]"));
        }

        @Test
        @DisplayName("ACCOUNTANT role is not permitted to approve [GH-90000]")
        void accountant_cannot_approve() { // GH-90000
            PolicyEvalResult result = evaluate("sox.transaction_approval", // GH-90000
                    Map.of("approver_role", "ACCOUNTANT", // GH-90000
                           "requester_id", "analyst-2",
                           "approver_id",  "accountant-bob"));
            assertThat(result.allowed()).isFalse(); // GH-90000
        }
    }

    // -------------------------------------------------------------------------
    // segregation_of_duties
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("segregation_of_duties [GH-90000]")
    class SegregationOfDuties {

        @Test
        @DisplayName("Different roles satisfy SoD [GH-90000]")
        void different_roles_ok() { // GH-90000
            PolicyEvalResult result = evaluate("sox.segregation_of_duties", // GH-90000
                    Map.of("requestor_role", "ACCOUNTANT", // GH-90000
                           "authorizer_role", "FINANCE_MANAGER"));
            assertThat(result.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Same role violates SoD [GH-90000]")
        void same_role_violates_sod() { // GH-90000
            PolicyEvalResult result = evaluate("sox.segregation_of_duties", // GH-90000
                    Map.of("requestor_role", "ACCOUNTANT", // GH-90000
                           "authorizer_role", "ACCOUNTANT"));
            assertThat(result.allowed()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("CFO as both requestor and authorizer violates SoD [GH-90000]")
        void same_cfo_role_violates() { // GH-90000
            PolicyEvalResult result = evaluate("sox.segregation_of_duties", // GH-90000
                    Map.of("requestor_role", "CFO", // GH-90000
                           "authorizer_role", "CFO"));
            assertThat(result.allowed()).isFalse(); // GH-90000
        }
    }

    // -------------------------------------------------------------------------
    // audit_trail_required
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Audit trail enabled allows operation [GH-90000]")
    void audit_trail_enabled_allowed() { // GH-90000
        PolicyEvalResult result = evaluate("sox.audit_trail_required", // GH-90000
                Map.of("operation", "ledger_post", "audit_trail_enabled", true)); // GH-90000
        assertThat(result.allowed()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Audit trail disabled denies operation [GH-90000]")
    void audit_trail_disabled_denied() { // GH-90000
        PolicyEvalResult result = evaluate("sox.audit_trail_required", // GH-90000
                Map.of("operation", "ledger_post", "audit_trail_enabled", false)); // GH-90000
        assertThat(result.allowed()).isFalse(); // GH-90000
        assertThat(result.reasons()).anyMatch(r -> r.contains("audit trail [GH-90000]"));
    }

    // -------------------------------------------------------------------------
    // large_transaction_review
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("large_transaction_review [GH-90000]")
    class LargeTransactionReview {

        @Test
        @DisplayName("Amount below threshold passes without secondary approver [GH-90000]")
        void small_amount_passes() { // GH-90000
            PolicyEvalResult result = evaluate("sox.large_transaction_review", // GH-90000
                    Map.of("amount_usd", 50000.0, // GH-90000
                           "approver_role", "FINANCE_ANALYST",
                           "secondary_approver_id", ""));
            assertThat(result.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Amount above threshold with CFO and secondary approver is allowed [GH-90000]")
        void large_amount_with_cfo_and_secondary_allowed() { // GH-90000
            PolicyEvalResult result = evaluate("sox.large_transaction_review", // GH-90000
                    Map.of("amount_usd", 200000.0, // GH-90000
                           "approver_role", "CFO",
                           "secondary_approver_id", "controller-bob"));
            assertThat(result.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Amount above threshold without secondary approver is denied [GH-90000]")
        void large_amount_without_secondary_denied() { // GH-90000
            PolicyEvalResult result = evaluate("sox.large_transaction_review", // GH-90000
                    Map.of("amount_usd", 200000.0, // GH-90000
                           "approver_role", "CFO",
                           "secondary_approver_id", ""));
            assertThat(result.allowed()).isFalse(); // GH-90000
            assertThat(result.reasons()).anyMatch(r -> r.contains("secondary approver [GH-90000]"));
        }

        @Test
        @DisplayName("Amount above threshold with wrong approver role is denied [GH-90000]")
        void large_amount_wrong_role_denied() { // GH-90000
            PolicyEvalResult result = evaluate("sox.large_transaction_review", // GH-90000
                    Map.of("amount_usd", 150000.0, // GH-90000
                           "approver_role", "ACCOUNTANT",
                           "secondary_approver_id", "controller-x"));
            assertThat(result.allowed()).isFalse(); // GH-90000
            assertThat(result.reasons()).anyMatch(r -> r.contains("CFO [GH-90000]") || r.contains("Controller [GH-90000]"));
        }
    }

    // -------------------------------------------------------------------------
    // cross-domain overlay: SOX on top of Nepal Healthcare
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("SOX and Nepal rules coexist in the same engine without collision [GH-90000]")
    void sox_and_nepal_rules_coexist() { // GH-90000
        NepalHealthcareRuleLibrary nepalLib = new NepalHealthcareRuleLibrary(); // GH-90000
        nepalLib.registerInto(engine);   // layer Nepal on top of existing SOX // GH-90000

        // Nepal-specific policy is accessible
        PolicyEvalResult nepalResult = evaluate("nepal_healthcare.consent_required", // GH-90000
                Map.of("operation", "data_export", "consent_on_file", true)); // GH-90000
        assertThat(nepalResult.allowed()).isTrue(); // GH-90000

        // SOX-specific policy still accessible
        PolicyEvalResult soxResult = evaluate("sox.audit_trail_required", // GH-90000
                Map.of("operation", "ledger_post", "audit_trail_enabled", true)); // GH-90000
        assertThat(soxResult.allowed()).isTrue(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private PolicyEvalResult evaluate(String policy, Map<String, Object> input) { // GH-90000
        return runPromise(() -> engine.evaluate("tenant-fin", policy, input)); // GH-90000
    }
}
