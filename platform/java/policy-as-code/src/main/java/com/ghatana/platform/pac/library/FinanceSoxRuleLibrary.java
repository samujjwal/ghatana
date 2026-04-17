package com.ghatana.platform.pac.library;

import com.ghatana.platform.pac.InMemoryPolicyEngine;
import com.ghatana.platform.pac.PolicyEvalResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rule library for Sarbanes-Oxley Act (SOX) financial controls.
 *
 * <p>Implements the segregation-of-duties, audit-trail, access-control, and approval
 * requirements relevant to SOX Section 302 and 404 compliance. The rules are written
 * against the {@link InMemoryPolicyEngine} for dev/test; in production environments
 * these same policy names should be backed by OPA documents.</p>
 *
 * <h3>Governed policy names</h3>
 * <ul>
 *   <li>{@code sox.financial_record_access}</li>
 *   <li>{@code sox.transaction_approval}</li>
 *   <li>{@code sox.segregation_of_duties}</li>
 *   <li>{@code sox.audit_trail_required}</li>
 *   <li>{@code sox.large_transaction_review}</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose SOX regulated rule library for financial controls
 * @doc.layer platform
 * @doc.pattern RuleLibrary
 * @since 1.0.0
 */
public final class FinanceSoxRuleLibrary implements RuleLibrary {

    /** Roles with read-only access to financial records. */
    private static final List<String> ALLOWED_FINANCE_ROLES =
            List.of("FINANCE_ANALYST", "ACCOUNTANT", "AUDITOR", "CFO", "CONTROLLER",
                    "COMPLIANCE_OFFICER");

    /** Roles permitted to approve transactions. */
    private static final List<String> APPROVAL_ROLES =
            List.of("FINANCE_MANAGER", "CFO", "CONTROLLER", "VP_FINANCE");

    /** Amount threshold (USD) above which a secondary approver is required (SOX §302). */
    private static final double LARGE_TRANSACTION_THRESHOLD_USD = 100_000.0;

    @Override
    public String id() {
        return "sox-financial-controls";
    }

    @Override
    public String version() {
        return "1.0.0";
    }

    @Override
    public String description() {
        return "Sarbanes-Oxley Act (SOX) §302/§404 financial controls — "
                + "access, approval, segregation of duties, audit trail, and large-transaction review";
    }

    @Override
    public void registerInto(InMemoryPolicyEngine engine) {
        engine.register("sox.financial_record_access",   this::evaluateRecordAccess);
        engine.register("sox.transaction_approval",      this::evaluateTransactionApproval);
        engine.register("sox.segregation_of_duties",     this::evaluateSegregationOfDuties);
        engine.register("sox.audit_trail_required",      this::evaluateAuditTrailRequired);
        engine.register("sox.large_transaction_review",  this::evaluateLargeTransactionReview);
    }

    // -------------------------------------------------------------------------
    // Rule: financial_record_access
    // Input keys: role (String), record_classification (String RESTRICTED|CONFIDENTIAL|PUBLIC)
    // -------------------------------------------------------------------------

    private PolicyEvalResult evaluateRecordAccess(Map<String, Object> input) {
        String role = asString(input, "role");
        String classification = asString(input, "record_classification");

        List<String> violations = new ArrayList<>();

        if (!ALLOWED_FINANCE_ROLES.contains(role)) {
            violations.add("Role '" + role + "' is not authorized to access financial records under SOX");
        }

        if ("RESTRICTED".equalsIgnoreCase(classification)
                && !List.of("CFO", "CONTROLLER", "AUDITOR").contains(role)) {
            violations.add("RESTRICTED financial records require CFO, Controller, or Auditor role (SOX §404)");
        }

        if (violations.isEmpty()) {
            return PolicyEvalResult.allow("sox.financial_record_access");
        }
        return PolicyEvalResult.deny("sox.financial_record_access", violations, 80);
    }

    // -------------------------------------------------------------------------
    // Rule: transaction_approval
    // Input keys: approver_role (String), requester_id (String), approver_id (String)
    // -------------------------------------------------------------------------

    private PolicyEvalResult evaluateTransactionApproval(Map<String, Object> input) {
        String approverRole = asString(input, "approver_role");
        String requesterId  = asString(input, "requester_id");
        String approverId   = asString(input, "approver_id");

        List<String> violations = new ArrayList<>();

        if (!APPROVAL_ROLES.contains(approverRole)) {
            violations.add("Approver role '" + approverRole + "' is not authorized to approve transactions"
                    + " under SOX §302");
        }

        // Requester cannot approve their own transaction (basic SoD)
        if (requesterId != null && requesterId.equalsIgnoreCase(approverId)) {
            violations.add("Self-approval is prohibited under SOX segregation-of-duties requirements");
        }

        if (violations.isEmpty()) {
            return PolicyEvalResult.allow("sox.transaction_approval");
        }
        return PolicyEvalResult.deny("sox.transaction_approval", violations, 90);
    }

    // -------------------------------------------------------------------------
    // Rule: segregation_of_duties
    // Input keys: requestor_role (String), authorizer_role (String)
    // -------------------------------------------------------------------------

    private PolicyEvalResult evaluateSegregationOfDuties(Map<String, Object> input) {
        String requestorRole  = asString(input, "requestor_role");
        String authorizerRole = asString(input, "authorizer_role");

        if (requestorRole != null && requestorRole.equalsIgnoreCase(authorizerRole)) {
            return PolicyEvalResult.deny(
                    "sox.segregation_of_duties",
                    List.of("The same role cannot both initiate and authorize a transaction under SOX SoD requirements"),
                    85);
        }

        // Accountant cannot authorize their own journal entries
        if ("ACCOUNTANT".equalsIgnoreCase(requestorRole) && "ACCOUNTANT".equalsIgnoreCase(authorizerRole)) {
            return PolicyEvalResult.deny(
                    "sox.segregation_of_duties",
                    List.of("ACCOUNTANT role cannot both record and authorize journal entries (SOX §404)"),
                    80);
        }

        return PolicyEvalResult.allow("sox.segregation_of_duties");
    }

    // -------------------------------------------------------------------------
    // Rule: audit_trail_required
    // Input keys: audit_trail_enabled (Boolean), operation (String)
    // -------------------------------------------------------------------------

    private PolicyEvalResult evaluateAuditTrailRequired(Map<String, Object> input) {
        boolean auditEnabled = asBoolean(input, "audit_trail_enabled");

        if (!auditEnabled) {
            String operation = asString(input, "operation");
            return PolicyEvalResult.deny(
                    "sox.audit_trail_required",
                    List.of("Operation '" + operation + "' requires audit trail logging under SOX §404"),
                    95);
        }
        return PolicyEvalResult.allow("sox.audit_trail_required");
    }

    // -------------------------------------------------------------------------
    // Rule: large_transaction_review
    // Input keys: amount_usd (Number), secondary_approver_id (String), approver_role (String)
    // -------------------------------------------------------------------------

    private PolicyEvalResult evaluateLargeTransactionReview(Map<String, Object> input) {
        double amount = asDouble(input, "amount_usd");
        String secondaryApproverId = asString(input, "secondary_approver_id");
        String approverRole        = asString(input, "approver_role");

        if (amount < LARGE_TRANSACTION_THRESHOLD_USD) {
            return PolicyEvalResult.allow("sox.large_transaction_review");
        }

        List<String> violations = new ArrayList<>();

        if (secondaryApproverId == null || secondaryApproverId.isBlank()) {
            violations.add("Transactions above $" + (int) LARGE_TRANSACTION_THRESHOLD_USD
                    + " require a secondary approver under SOX §302");
        }

        if (!List.of("CFO", "CONTROLLER", "VP_FINANCE").contains(approverRole)) {
            violations.add("Large transactions must be approved by CFO, Controller, or VP Finance");
        }

        if (violations.isEmpty()) {
            return PolicyEvalResult.allow("sox.large_transaction_review");
        }
        return PolicyEvalResult.deny("sox.large_transaction_review", violations, 90);
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

    private static double asDouble(Map<String, Object> input, String key) {
        Object v = input.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) { }
        }
        return 0.0;
    }
}
