package com.ghatana.plugin.compliance.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.plugin.compliance.CompliancePlugin;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Standard implementation of CompliancePlugin.
 *
 * <p>This implementation provides:</p>
 * <ul>
 *   <li>Rule-based compliance checking</li>
 *   <li>SOX, PCI-DSS, HIPAA, GDPR support</li>
 *   <li>Audit trail generation</li>
 *   <li>Violation tracking and reporting</li>
 * </ul>
 *
 * <p>Products can register custom rule sets and extend with product-specific logic.</p>
 *
 * @doc.type class
 * @doc.purpose Standard compliance rule engine implementation
 * @doc.layer platform
 * @doc.pattern Plugin Implementation
 * @since 1.0.0
 */
public class StandardCompliancePlugin implements CompliancePlugin {

    private static final Logger LOG = LoggerFactory.getLogger(StandardCompliancePlugin.class);

    private final Map<String, List<ComplianceRule>> ruleSets = new ConcurrentHashMap<>();
    private final Map<String, List<ComplianceViolation>> activeViolations = new ConcurrentHashMap<>();
    private final Map<String, List<AuditEntry>> auditTrails = new ConcurrentHashMap<>();
    private static final PluginMetadata METADATA = PluginMetadata.builder()
        .id("com.ghatana.plugin.compliance")
        .name("Compliance Plugin")
        .version("1.0.0")
        .description("Generic regulatory compliance rule engine")
        .type(PluginType.GOVERNANCE)
        .author("Ghatana")
        .license("Proprietary")
        .capability("compliance:evaluate", "compliance:audit")
        .build();

    private PluginContext context;
    private PluginState state = PluginState.UNLOADED;

    // Pre-defined rule sets for common regulations
    private static final Map<String, List<ComplianceRule>> STANDARD_RULE_SETS;

    static {
        Map<String, List<ComplianceRule>> standards = new HashMap<>();

        // SOX rules
        standards.put("SOX", Arrays.asList(
            new ComplianceRule("SOX-001", "SEPARATION_OF_DUTIES",
                "Financial operations require approval from separate user", ComplianceRule.Severity.HIGH, "approval_required"),
            new ComplianceRule("SOX-002", "AUDIT_TRAIL",
                "All financial changes must be logged", ComplianceRule.Severity.CRITICAL, "audit_required")
        ));

        // HIPAA rules
        standards.put("HIPAA", Arrays.asList(
            new ComplianceRule("HIPAA-001", "PHI_ACCESS_CONTROL",
                "PHI access requires authentication", ComplianceRule.Severity.CRITICAL, "authentication_required"),
            new ComplianceRule("HIPAA-002", "MINIMUM_NECESSARY",
                "Access limited to minimum necessary", ComplianceRule.Severity.HIGH, "need_to_know")
        ));

        // GDPR rules
        standards.put("GDPR", Arrays.asList(
            new ComplianceRule("GDPR-001", "CONSENT_REQUIRED",
                "Data processing requires consent", ComplianceRule.Severity.CRITICAL, "consent_check"),
            new ComplianceRule("GDPR-002", "RIGHT_TO_ERASURE",
                "Users can request data deletion", ComplianceRule.Severity.HIGH, "deletion_request")
        ));

        // PCI-DSS rules
        standards.put("PCI-DSS", Arrays.asList(
            new ComplianceRule("PCI-001", "ENCRYPTION",
                "Cardholder data must be encrypted", ComplianceRule.Severity.CRITICAL, "encryption_required"),
            new ComplianceRule("PCI-002", "ACCESS_CONTROL",
                "Restrict access to cardholder data", ComplianceRule.Severity.CRITICAL, "access_restriction")
        ));

        STANDARD_RULE_SETS = Collections.unmodifiableMap(standards);
    }

    public String getId() {
        return "compliance-plugin";
    }

    public String getName() {
        return "Compliance Plugin";
    }

    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public PluginMetadata metadata() {
        return METADATA;
    }

    @Override
    public PluginState getState() {
        return state;
    }

    @Override
    public Promise<Void> initialize(PluginContext context) {
        this.context = context;
        this.state = PluginState.INITIALIZED;

        // Load standard rule sets
        STANDARD_RULE_SETS.forEach(ruleSets::putIfAbsent);

        LOG.info("CompliancePlugin initialized with {} standard rule sets", STANDARD_RULE_SETS.size());
        return Promise.complete();
    }

    @Override
    public Promise<Void> start() {
        this.state = PluginState.RUNNING;
        LOG.info("CompliancePlugin started");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        this.state = PluginState.STOPPED;
        LOG.info("CompliancePlugin stopped");
        return Promise.complete();
    }

    @Override
    public Promise<Void> shutdown() {
        ruleSets.clear();
        activeViolations.clear();
        auditTrails.clear();
        this.state = PluginState.UNLOADED;
        LOG.info("CompliancePlugin shutdown");
        return Promise.complete();
    }

    @Override
    public Promise<ComplianceResult> evaluate(String ruleSetId, ComplianceContext context) {
        List<ComplianceRule> rules = ruleSets.getOrDefault(ruleSetId, Collections.emptyList());
        List<ComplianceViolation> violations = new ArrayList<>();

        for (ComplianceRule rule : rules) {
            boolean rulePassed = evaluateRule(rule, context);
            if (!rulePassed) {
                violations.add(new ComplianceViolation(
                    rule.ruleId(),
                    rule.description(),
                    rule.severity(),
                    Map.of("condition", rule.condition())
                ));
            }
        }

        boolean compliant = violations.isEmpty();

        // Log audit entry
        String entryId = UUID.randomUUID().toString();
        AuditEntry entry = new AuditEntry(
            entryId,
            context.entityId(),
            compliant ? "COMPLIANCE_CHECK_PASSED" : "COMPLIANCE_CHECK_FAILED",
            ruleSetId,
            violations.isEmpty() ? "All rules passed" : violations.size() + " violations found",
            Instant.now()
        );

        auditTrails.computeIfAbsent(context.entityId(), k -> new ArrayList<>()).add(entry);

        // Store active violations
        if (!violations.isEmpty()) {
            activeViolations.put(context.entityId(), violations);
        }

        ComplianceResult result = new ComplianceResult(
            compliant,
            violations,
            ruleSetId,
            Instant.now()
        );

        LOG.info("Compliance evaluation for {} against {}: compliant={}, violations={}",
            context.entityId(), ruleSetId, compliant, violations.size());

        return Promise.of(result);
    }

    @Override
    public Promise<Void> registerRuleSet(String ruleSetId, List<ComplianceRule> rules) {
        ruleSets.put(ruleSetId, new ArrayList<>(rules));
        LOG.info("Registered rule set {} with {} rules", ruleSetId, rules.size());
        return Promise.complete();
    }

    @Override
    public Promise<Void> addRule(String ruleSetId, ComplianceRule rule) {
        ruleSets.computeIfAbsent(ruleSetId, k -> new ArrayList<>()).add(rule);
        LOG.info("Added rule {} to rule set {}", rule.ruleId(), ruleSetId);
        return Promise.complete();
    }

    @Override
    public Promise<List<AuditEntry>> getAuditTrail(String entityId) {
        return Promise.of(auditTrails.getOrDefault(entityId, Collections.emptyList()));
    }

    @Override
    public Promise<List<ComplianceViolation>> getActiveViolations(String ruleSetId) {
        return Promise.of(
            activeViolations.values().stream()
                .flatMap(List::stream)
                .filter(v -> v.ruleId().startsWith(ruleSetId))
                .collect(Collectors.toList())
        );
    }

    private boolean evaluateRule(ComplianceRule rule, ComplianceContext context) {
        Map<String, Object> data = context.data();

        // Rule evaluation based on rule condition
        // Tests use condition names as boolean keys (e.g., audit_required=true)
        boolean result;
        switch (rule.condition()) {
            case "approval_required":
                result = Boolean.TRUE.equals(data.get("approval_required"));
                break;
            case "audit_required":
                result = Boolean.TRUE.equals(data.get("audit_required"));
                break;
            case "authentication_required":
                result = Boolean.TRUE.equals(data.get("authenticated"));
                break;
            case "need_to_know":
                result = Boolean.TRUE.equals(data.get("need_to_know"));
                break;
            case "consent_check":
                result = Boolean.TRUE.equals(data.get("consentGiven"));
                break;
            case "encryption_required":
                result = Boolean.TRUE.equals(data.get("encrypted"));
                break;
            case "access_restriction":
                result = data.containsKey("accessRole");
                break;
            case "BLOCK_ALWAYS":
            case "block":
                // Custom blocking rule always fails
                result = false;
                break;
            default:
                // Default: assume compliant for unknown rule types
                LOG.warn("Unknown rule condition: {}", rule.condition());
                result = true;
                break;
        }

        LOG.debug("Rule {} (condition={}): data={}, result={}", rule.ruleId(), rule.condition(), data, result);
        return result;
    }

    @Override
    public String toString() {
        return "StandardCompliancePlugin{ruleSets=" + ruleSets.size() +
               ", violations=" + activeViolations.size() + "}";
    }
}
