/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Evaluates governance policies against data artifacts.
 *
 * <p>Each call to {@link #evaluate} inspects the policy conditions against the
 * supplied record and returns any detected {@link PolicyViolation}s. An empty
 * list means the record passes the policy. Callers are responsible for deciding
 * what to do on violation (block, alert, remediate).
 *
 * <p>This class is stateless and thread-safe.
 *
 * @doc.type class
 * @doc.purpose Evaluate governance policies against data artifacts and surface violations
 * @doc.layer product
 * @doc.pattern PolicyEvaluator
 */
public final class PolicyEvaluator {

    /**
     * Represents a detected policy violation.
     *
     * @param policyId   the id of the violated policy
     * @param policyName the name of the violated policy
     * @param type       the policy type
     * @param field      the record field involved in the violation (may be {@code null})
     * @param reason     human-readable violation description
     */
    public record PolicyViolation(
            String policyId,
            String policyName,
            PolicyService.PolicyType type,
            String field,
            String reason) {}

    /**
     * Context supplied alongside the artifact when evaluating a policy.
     *
     * @param tenantId  the requesting tenant identifier
     * @param principal the identity (user or service) accessing the data
     * @param purpose   the declared purpose for which data is being used
     * @param region    the geographic region of the requesting node
     */
    public record EvaluationContext(
            String tenantId,
            String principal,
            String purpose,
            String region) {}

    /** Known PII field name fragments — any field containing these tokens is PII. */
    private static final Set<String> PII_FRAGMENTS = Set.of(
            "email", "phone", "ssn", "password", "dob", "date_of_birth",
            "credit_card", "card_number", "national_id", "passport");

    /** Fields that must always be present for audit logging. */
    private static final Set<String> REQUIRED_AUDIT_FIELDS = Set.of(
            "created_at", "updated_by");

    /**
     * Evaluates a single policy against the given record and context.
     *
     * @param policy  the policy to evaluate; must not be {@code null}
     * @param record  the data artifact to inspect; must not be {@code null}
     * @param context evaluation context; must not be {@code null}
     * @return list of violations — empty if the record is compliant with this policy
     */
    public List<PolicyViolation> evaluate(
            PolicyService.Policy policy,
            Map<String, Object> record,
            EvaluationContext context) {

        Objects.requireNonNull(policy,  "policy must not be null");
        Objects.requireNonNull(record,  "record must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (!policy.enabled()) {
            return Collections.emptyList();
        }

        return switch (policy.type()) {
            case PII_MASKING          -> evaluatePiiMasking(policy, record);
            case ACCESS_CONTROL       -> evaluateAccessControl(policy, record, context);
            case AUDIT_LOGGING        -> evaluateAuditLogging(policy, record);
            case CONSENT_MANAGEMENT   -> evaluateConsentManagement(policy, record, context);
            case DATA_MINIMIZATION    -> evaluateDataMinimization(policy, record);
            case PURPOSE_LIMITATION   -> evaluatePurposeLimitation(policy, record, context);
            case CROSS_BORDER_TRANSFER -> evaluateCrossBorderTransfer(policy, record, context);
            case ENCRYPTION           -> evaluateEncryption(policy, record);
            case ANONYMIZATION        -> evaluateAnonymization(policy, record);
            case DATA_RETENTION       -> evaluateDataRetention(policy, record);
        };
    }

    /**
     * Evaluates all enabled policies that match the given tenant against the record.
     *
     * @param policies list of candidate policies
     * @param record   data artifact to inspect
     * @param context  evaluation context
     * @return aggregated list of all violations across all matching policies
     */
    public List<PolicyViolation> evaluateAll(
            List<PolicyService.Policy> policies,
            Map<String, Object> record,
            EvaluationContext context) {

        Objects.requireNonNull(policies, "policies must not be null");
        Objects.requireNonNull(record,   "record must not be null");
        Objects.requireNonNull(context,  "context must not be null");

        List<PolicyViolation> violations = new ArrayList<>();
        for (PolicyService.Policy policy : policies) {
            if (policy.tenantId() == null
                    || policy.tenantId().equals(context.tenantId())) {
                violations.addAll(evaluate(policy, record, context));
            }
        }
        return violations;
    }

    // ─── Policy-type handlers ─────────────────────────────────────────────────

    private List<PolicyViolation> evaluatePiiMasking(
            PolicyService.Policy policy,
            Map<String, Object> record) {

        List<PolicyViolation> violations = new ArrayList<>();
        for (String field : record.keySet()) {
            if (isPiiField(field)) {
                Object value = record.get(field);
                if (value != null && !isMasked(value)) {
                    violations.add(new PolicyViolation(
                            policy.id(),
                            policy.name(),
                            policy.type(),
                            field,
                            "PII field '" + field + "' is not masked"));
                }
            }
        }
        return violations;
    }

    private List<PolicyViolation> evaluateAccessControl(
            PolicyService.Policy policy,
            Map<String, Object> record,
            EvaluationContext context) {

        List<PolicyViolation> violations = new ArrayList<>();
        List<String> conditions = policy.conditions();
        if (conditions == null || conditions.isEmpty()) {
            return violations;
        }

        // Conditions are of the form "ALLOW:<principal>" or "DENY:<principal>"
        boolean explicitDeny = conditions.stream()
                .filter(c -> c.startsWith("DENY:"))
                .map(c -> c.substring("DENY:".length()))
                .anyMatch(p -> p.equals(context.principal()));

        if (explicitDeny) {
            violations.add(new PolicyViolation(
                    policy.id(),
                    policy.name(),
                    policy.type(),
                    null,
                    "Principal '" + context.principal() + "' is explicitly denied by access-control policy"));
        }
        return violations;
    }

    private List<PolicyViolation> evaluateAuditLogging(
            PolicyService.Policy policy,
            Map<String, Object> record) {

        List<PolicyViolation> violations = new ArrayList<>();
        for (String required : REQUIRED_AUDIT_FIELDS) {
            if (!record.containsKey(required)) {
                violations.add(new PolicyViolation(
                        policy.id(),
                        policy.name(),
                        policy.type(),
                        required,
                        "Required audit field '" + required + "' is missing"));
            }
        }
        return violations;
    }

    private List<PolicyViolation> evaluateConsentManagement(
            PolicyService.Policy policy,
            Map<String, Object> record,
            EvaluationContext context) {

        List<PolicyViolation> violations = new ArrayList<>();
        // A record must carry a non-null "consent_status" field
        Object consent = record.get("consent_status");
        if (consent == null) {
            violations.add(new PolicyViolation(
                    policy.id(),
                    policy.name(),
                    policy.type(),
                    "consent_status",
                    "Record is missing 'consent_status'"));
        } else if (!"GRANTED".equalsIgnoreCase(consent.toString())) {
            violations.add(new PolicyViolation(
                    policy.id(),
                    policy.name(),
                    policy.type(),
                    "consent_status",
                    "Consent is not GRANTED (found: " + consent + ")"));
        }
        return violations;
    }

    private List<PolicyViolation> evaluateDataMinimization(
            PolicyService.Policy policy,
            Map<String, Object> record) {

        List<PolicyViolation> violations = new ArrayList<>();
        List<String> allowedFields = policy.conditions();
        if (allowedFields == null || allowedFields.isEmpty()) {
            return violations;
        }

        for (String field : record.keySet()) {
            if (!allowedFields.contains(field)) {
                violations.add(new PolicyViolation(
                        policy.id(),
                        policy.name(),
                        policy.type(),
                        field,
                        "Field '" + field + "' is not in the data-minimization allowed list"));
            }
        }
        return violations;
    }

    private List<PolicyViolation> evaluatePurposeLimitation(
            PolicyService.Policy policy,
            Map<String, Object> record,
            EvaluationContext context) {

        List<PolicyViolation> violations = new ArrayList<>();
        List<String> allowedPurposes = policy.conditions();
        if (allowedPurposes == null || allowedPurposes.isEmpty()) {
            return violations;
        }

        if (context.purpose() == null || !allowedPurposes.contains(context.purpose())) {
            violations.add(new PolicyViolation(
                    policy.id(),
                    policy.name(),
                    policy.type(),
                    null,
                    "Purpose '" + context.purpose() + "' is not in the allowed list: " + allowedPurposes));
        }
        return violations;
    }

    private List<PolicyViolation> evaluateCrossBorderTransfer(
            PolicyService.Policy policy,
            Map<String, Object> record,
            EvaluationContext context) {

        List<PolicyViolation> violations = new ArrayList<>();
        List<String> allowedRegions = policy.conditions();
        if (allowedRegions == null || allowedRegions.isEmpty()) {
            return violations;
        }

        if (context.region() == null || !allowedRegions.contains(context.region())) {
            violations.add(new PolicyViolation(
                    policy.id(),
                    policy.name(),
                    policy.type(),
                    null,
                    "Region '" + context.region() + "' is not in the allowed list: " + allowedRegions));
        }
        return violations;
    }

    private List<PolicyViolation> evaluateEncryption(
            PolicyService.Policy policy,
            Map<String, Object> record) {

        List<PolicyViolation> violations = new ArrayList<>();
        // The record must carry an "encryption_status" field with value "ENCRYPTED"
        Object status = record.get("encryption_status");
        if (status == null || !"ENCRYPTED".equalsIgnoreCase(status.toString())) {
            violations.add(new PolicyViolation(
                    policy.id(),
                    policy.name(),
                    policy.type(),
                    "encryption_status",
                    "Record does not carry encryption_status=ENCRYPTED (found: " + status + ")"));
        }
        return violations;
    }

    private List<PolicyViolation> evaluateAnonymization(
            PolicyService.Policy policy,
            Map<String, Object> record) {

        List<PolicyViolation> violations = new ArrayList<>();
        for (String field : record.keySet()) {
            if (isPiiField(field)) {
                Object value = record.get(field);
                if (value != null && !isAnonymized(value)) {
                    violations.add(new PolicyViolation(
                            policy.id(),
                            policy.name(),
                            policy.type(),
                            field,
                            "PII field '" + field + "' appears not to be anonymized"));
                }
            }
        }
        return violations;
    }

    private List<PolicyViolation> evaluateDataRetention(
            PolicyService.Policy policy,
            Map<String, Object> record) {

        List<PolicyViolation> violations = new ArrayList<>();
        // The record must carry a "retention_expires_at" field
        Object expiresAt = record.get("retention_expires_at");
        if (expiresAt == null) {
            violations.add(new PolicyViolation(
                    policy.id(),
                    policy.name(),
                    policy.type(),
                    "retention_expires_at",
                    "Record is missing required 'retention_expires_at' field"));
        }
        return violations;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static boolean isPiiField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase();
        return PII_FRAGMENTS.stream().anyMatch(lower::contains);
    }

    /**
     * A value is considered masked if it is a {@link String} that consists
     * entirely of mask characters ({@code *}, {@code #}, {@code X}) or
     * follows the redaction pattern {@code [REDACTED]}.
     */
    private static boolean isMasked(Object value) {
        if (!(value instanceof String s)) {
            return false;
        }
        return s.isBlank()
                || s.matches("[*#X]+")
                || s.matches("\\[REDACTED\\]")
                || s.matches("\\*+.*\\*+");
    }

    /**
     * A value is considered anonymized if it is {@code null}, blank, a
     * recognised placeholder, or a UUID-style token that replaced the real value.
     */
    private static boolean isAnonymized(Object value) {
        if (value == null) {
            return true;
        }
        String s = value.toString();
        return s.isBlank()
                || s.equals("[ANONYMIZED]")
                || s.equals("[REMOVED]")
                || s.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }
}
