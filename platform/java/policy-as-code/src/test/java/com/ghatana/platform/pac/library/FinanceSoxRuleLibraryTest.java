/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("FinanceSoxRuleLibrary")
class FinanceSoxRuleLibraryTest extends EventloopTestBase {

    private InMemoryPolicyEngine engine;
    private final FinanceSoxRuleLibrary library = new FinanceSoxRuleLibrary(); 

    @BeforeEach
    void setUp() { 
        engine = new InMemoryPolicyEngine(); 
        library.registerInto(engine); 
    }

    // -------------------------------------------------------------------------
    // metadata
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("id and version are populated")
    void metadata() { 
        assertThat(library.id()).isEqualTo("sox-financial-controls");
        assertThat(library.version()).isEqualTo("1.0.0");
        assertThat(library.description()).isNotBlank(); 
    }

    // -------------------------------------------------------------------------
    // financial_record_access
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("financial_record_access")
    class RecordAccess {

        @Test
        @DisplayName("AUDITOR accessing RESTRICTED records is allowed")
        void auditor_restricted_allowed() { 
            PolicyEvalResult result = evaluate("sox.financial_record_access", 
                    Map.of("role", "AUDITOR", "record_classification", "RESTRICTED")); 
            assertThat(result.allowed()).isTrue(); 
        }

        @Test
        @DisplayName("FINANCE_ANALYST accessing RESTRICTED records is denied")
        void analyst_restricted_denied() { 
            PolicyEvalResult result = evaluate("sox.financial_record_access", 
                    Map.of("role", "FINANCE_ANALYST", "record_classification", "RESTRICTED")); 
            assertThat(result.allowed()).isFalse(); 
            assertThat(result.reasons()).anyMatch(r -> r.contains("RESTRICTED"));
        }

        @Test
        @DisplayName("ENGINEER role (not finance) is denied")
        void engineer_denied() { 
            PolicyEvalResult result = evaluate("sox.financial_record_access", 
                    Map.of("role", "ENGINEER", "record_classification", "CONFIDENTIAL")); 
            assertThat(result.allowed()).isFalse(); 
        }

        @Test
        @DisplayName("CFO accessing any record is allowed")
        void cfo_allowed() { 
            PolicyEvalResult result = evaluate("sox.financial_record_access", 
                    Map.of("role", "CFO", "record_classification", "RESTRICTED")); 
            assertThat(result.allowed()).isTrue(); 
        }
    }

    // -------------------------------------------------------------------------
    // transaction_approval
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("transaction_approval")
    class TransactionApproval {

        @Test
        @DisplayName("CFO approving a transaction by another user is allowed")
        void cfo_approves_different_user() { 
            PolicyEvalResult result = evaluate("sox.transaction_approval", 
                    Map.of("approver_role", "CFO", 
                           "requester_id", "analyst-1",
                           "approver_id",  "cfo-jane"));
            assertThat(result.allowed()).isTrue(); 
        }

        @Test
        @DisplayName("Self-approval is denied regardless of role")
        void self_approval_denied() { 
            PolicyEvalResult result = evaluate("sox.transaction_approval", 
                    Map.of("approver_role", "CFO", 
                           "requester_id", "cfo-jane",
                           "approver_id",  "cfo-jane"));
            assertThat(result.allowed()).isFalse(); 
            assertThat(result.reasons()).anyMatch(r -> r.contains("Self-approval"));
        }

        @Test
        @DisplayName("ACCOUNTANT role is not permitted to approve")
        void accountant_cannot_approve() { 
            PolicyEvalResult result = evaluate("sox.transaction_approval", 
                    Map.of("approver_role", "ACCOUNTANT", 
                           "requester_id", "analyst-2",
                           "approver_id",  "accountant-bob"));
            assertThat(result.allowed()).isFalse(); 
        }
    }

    // -------------------------------------------------------------------------
    // segregation_of_duties
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("segregation_of_duties")
    class SegregationOfDuties {

        @Test
        @DisplayName("Different roles satisfy SoD")
        void different_roles_ok() { 
            PolicyEvalResult result = evaluate("sox.segregation_of_duties", 
                    Map.of("requestor_role", "ACCOUNTANT", 
                           "authorizer_role", "FINANCE_MANAGER"));
            assertThat(result.allowed()).isTrue(); 
        }

        @Test
        @DisplayName("Same role violates SoD")
        void same_role_violates_sod() { 
            PolicyEvalResult result = evaluate("sox.segregation_of_duties", 
                    Map.of("requestor_role", "ACCOUNTANT", 
                           "authorizer_role", "ACCOUNTANT"));
            assertThat(result.allowed()).isFalse(); 
        }

        @Test
        @DisplayName("CFO as both requestor and authorizer violates SoD")
        void same_cfo_role_violates() { 
            PolicyEvalResult result = evaluate("sox.segregation_of_duties", 
                    Map.of("requestor_role", "CFO", 
                           "authorizer_role", "CFO"));
            assertThat(result.allowed()).isFalse(); 
        }
    }

    // -------------------------------------------------------------------------
    // audit_trail_required
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Audit trail enabled allows operation")
    void audit_trail_enabled_allowed() { 
        PolicyEvalResult result = evaluate("sox.audit_trail_required", 
                Map.of("operation", "ledger_post", "audit_trail_enabled", true)); 
        assertThat(result.allowed()).isTrue(); 
    }

    @Test
    @DisplayName("Audit trail disabled denies operation")
    void audit_trail_disabled_denied() { 
        PolicyEvalResult result = evaluate("sox.audit_trail_required", 
                Map.of("operation", "ledger_post", "audit_trail_enabled", false)); 
        assertThat(result.allowed()).isFalse(); 
        assertThat(result.reasons()).anyMatch(r -> r.contains("audit trail"));
    }

    // -------------------------------------------------------------------------
    // large_transaction_review
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("large_transaction_review")
    class LargeTransactionReview {

        @Test
        @DisplayName("Amount below threshold passes without secondary approver")
        void small_amount_passes() { 
            PolicyEvalResult result = evaluate("sox.large_transaction_review", 
                    Map.of("amount_usd", 50000.0, 
                           "approver_role", "FINANCE_ANALYST",
                           "secondary_approver_id", ""));
            assertThat(result.allowed()).isTrue(); 
        }

        @Test
        @DisplayName("Amount above threshold with CFO and secondary approver is allowed")
        void large_amount_with_cfo_and_secondary_allowed() { 
            PolicyEvalResult result = evaluate("sox.large_transaction_review", 
                    Map.of("amount_usd", 200000.0, 
                           "approver_role", "CFO",
                           "secondary_approver_id", "controller-bob"));
            assertThat(result.allowed()).isTrue(); 
        }

        @Test
        @DisplayName("Amount above threshold without secondary approver is denied")
        void large_amount_without_secondary_denied() { 
            PolicyEvalResult result = evaluate("sox.large_transaction_review", 
                    Map.of("amount_usd", 200000.0, 
                           "approver_role", "CFO",
                           "secondary_approver_id", ""));
            assertThat(result.allowed()).isFalse(); 
            assertThat(result.reasons()).anyMatch(r -> r.contains("secondary approver"));
        }

        @Test
        @DisplayName("Amount above threshold with wrong approver role is denied")
        void large_amount_wrong_role_denied() { 
            PolicyEvalResult result = evaluate("sox.large_transaction_review", 
                    Map.of("amount_usd", 150000.0, 
                           "approver_role", "ACCOUNTANT",
                           "secondary_approver_id", "controller-x"));
            assertThat(result.allowed()).isFalse(); 
            assertThat(result.reasons()).anyMatch(r -> r.contains("CFO") || r.contains("Controller"));
        }
    }

    // -------------------------------------------------------------------------
    // cross-domain overlay: SOX on top of Nepal Healthcare
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("SOX and Nepal rules coexist in the same engine without collision")
    void sox_and_nepal_rules_coexist() { 
        NepalHealthcareRuleLibrary nepalLib = new NepalHealthcareRuleLibrary(); 
        nepalLib.registerInto(engine);   // layer Nepal on top of existing SOX 

        // Nepal-specific policy is accessible
        PolicyEvalResult nepalResult = evaluate("nepal_healthcare.consent_required", 
                Map.of("operation", "data_export", "consent_on_file", true)); 
        assertThat(nepalResult.allowed()).isTrue(); 

        // SOX-specific policy still accessible
        PolicyEvalResult soxResult = evaluate("sox.audit_trail_required", 
                Map.of("operation", "ledger_post", "audit_trail_enabled", true)); 
        assertThat(soxResult.allowed()).isTrue(); 
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private PolicyEvalResult evaluate(String policy, Map<String, Object> input) { 
        return runPromise(() -> engine.evaluate("tenant-fin", policy, input)); 
    }
}
