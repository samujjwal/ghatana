package com.ghatana.plugin.compliance;

import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Compliance Plugin - Generic compliance rule engine.
 *
 * <p>Products register domain-specific rule packs via {@link #registerRuleSet}. The engine
 * evaluates entities against registered rules without built-in regulatory assumptions.</p>
 *
 * @doc.type interface
 * @doc.purpose Generic compliance rule engine — rule packs are product-supplied
 * @doc.layer platform
 * @doc.pattern Plugin
 * @since 1.0.0
 */
public interface CompliancePlugin extends Plugin {

    /**
     * Evaluates an entity against a rule set.
     *
     * @param ruleSetId the rule set identifier
     * @param context the compliance context
     * @return Promise containing the compliance result
     */
    Promise<ComplianceResult> evaluate(String ruleSetId, ComplianceContext context);

    /**
     * Registers a rule set.
     *
     * @param ruleSetId the rule set identifier
     * @param rules the compliance rules
     * @return Promise completing when rules are registered
     */
    Promise<Void> registerRuleSet(String ruleSetId, List<ComplianceRule> rules);

    /**
     * Adds a single rule to a rule set.
     *
     * @param ruleSetId the rule set identifier
     * @param rule the compliance rule
     * @return Promise completing when rule is added
     */
    Promise<Void> addRule(String ruleSetId, ComplianceRule rule);

    /**
     * Gets the audit trail for an entity.
     *
     * @param entityId the entity identifier
     * @return Promise containing audit entries
     */
    Promise<List<AuditEntry>> getAuditTrail(String entityId);

    /**
     * Gets active compliance violations for a rule set.
     *
     * @param ruleSetId the rule set identifier
     * @return Promise containing active violations
     */
    Promise<List<ComplianceViolation>> getActiveViolations(String ruleSetId);

    /**
     * Compliance context.
     */
    record ComplianceContext(
        String entityId,
        String entityType,
        Map<String, Object> data,
        String principalId,
        Instant timestamp
    ) {}

    /**
     * Compliance rule.
     */
    record ComplianceRule(
        String ruleId,
        String ruleType,
        String description,
        Severity severity,
        String condition
    ) {
        public enum Severity {
            LOW, MEDIUM, HIGH, CRITICAL
        }
    }

    /**
     * Compliance result.
     */
    record ComplianceResult(
        boolean compliant,
        List<ComplianceViolation> violations,
        String ruleSetId,
        Instant evaluatedAt
    ) {}

    /**
     * Compliance violation.
     */
    record ComplianceViolation(
        String ruleId,
        String description,
        ComplianceRule.Severity severity,
        Map<String, Object> details
    ) {}

    /**
     * Audit entry.
     */
    record AuditEntry(
        String entryId,
        String entityId,
        String action,
        String ruleId,
        String details,
        Instant timestamp
    ) {}
}
