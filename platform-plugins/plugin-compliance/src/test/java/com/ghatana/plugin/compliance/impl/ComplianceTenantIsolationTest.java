package com.ghatana.plugin.compliance.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.compliance.CompliancePlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * KP-CPL: Tenant isolation and deterministic fixture conformance tests
 * for {@link StandardCompliancePlugin}.
 *
 * <p>Covers:</p>
 * <ul>
 *   <li>KP-CPL-001: rule set isolation — rules registered for rule-set A must not affect rule-set B evaluation</li>
 *   <li>KP-CPL-002: SOX audit-required deterministic fixture — entities missing audit flag must be non-compliant</li>
 *   <li>KP-CPL-003: HIPAA PHI access control — entities without authentication must violate HIPAA-001</li>
 *   <li>KP-CPL-004: audit trail isolation — entity A's audit trail must not include entity B's entries</li>
 *   <li>KP-CPL-005: active violations isolation — violations for rule-set A must not appear in rule-set B</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose KP-CPL compliance plugin tenant isolation and deterministic fixture tests
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("CompliancePlugin — tenant isolation and deterministic fixture tests")
@ExtendWith(MockitoExtension.class)
class ComplianceTenantIsolationTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardCompliancePlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new StandardCompliancePlugin();
        runPromise(() -> plugin.initialize(mockContext).then(v -> plugin.start()));
    }

    // ── KP-CPL-001: Rule set isolation ───────────────────────────────────────

    @Test
    @DisplayName("KP-CPL-001: custom rule set for finance must not interfere with phr rule set")
    void testRuleSetIsolation() {
        // Register a blocking rule only for finance
        CompliancePlugin.ComplianceRule blockAll = new CompliancePlugin.ComplianceRule(
                "FIN-BLOCK-ALL", "BLOCK_ALWAYS", "Always block for testing",
                CompliancePlugin.ComplianceRule.Severity.CRITICAL, "block");
        runPromise(() -> plugin.registerRuleSet("finance-ruleset",
                List.of(blockAll)));

        CompliancePlugin.ComplianceContext financeCtx = new CompliancePlugin.ComplianceContext(
                "trade-001", "TRADE", Map.of(), "trader-1", Instant.now());
        CompliancePlugin.ComplianceContext phrCtx = new CompliancePlugin.ComplianceContext(
                "patient-001", "PATIENT", Map.of("authenticated", true), "nurse-1", Instant.now());

        CompliancePlugin.ComplianceResult financeResult = runPromise(() -> plugin.evaluate("finance-ruleset", financeCtx));
        CompliancePlugin.ComplianceResult phrResult = runPromise(() -> plugin.evaluate("HIPAA", phrCtx));

        assertThat(financeResult.compliant())
                .as("finance rule set should fail due to blocking rule")
                .isFalse();
        // PHR context evaluated against HIPAA — must not be contaminated by finance rule
        assertThat(phrResult.violations())
                .as("PHR evaluation must not contain finance-specific violations")
                .noneMatch(v -> v.ruleId().startsWith("FIN-"));
    }

    // ── KP-CPL-002: SOX deterministic fixture ────────────────────────────────

    @Test
    @DisplayName("KP-CPL-002: entity missing 'audit_required' flag must violate SOX-002")
    void testSoxAuditRequiredViolation() {
        CompliancePlugin.ComplianceContext ctx = new CompliancePlugin.ComplianceContext(
                "fin-txn-" + UUID.randomUUID(), "FINANCIAL_TRANSACTION",
                Map.of("amount", 10_000, "approval_required", true),
                "accountant-1", Instant.now());

        CompliancePlugin.ComplianceResult result = runPromise(() -> plugin.evaluate("SOX", ctx));

        assertThat(result.compliant()).isFalse();
        assertThat(result.violations())
                .as("missing audit flag must produce SOX-002 violation")
                .anyMatch(v -> v.ruleId().equals("SOX-002"));
    }

    @Test
    @DisplayName("KP-CPL-002: entity with both audit and approval flags must be SOX compliant")
    void testSoxCompliantEntity() {
        CompliancePlugin.ComplianceContext ctx = new CompliancePlugin.ComplianceContext(
                "fin-txn-" + UUID.randomUUID(), "FINANCIAL_TRANSACTION",
                Map.of("amount", 10_000, "approval_required", true, "audit_required", true),
                "accountant-1", Instant.now());

        CompliancePlugin.ComplianceResult result = runPromise(() -> plugin.evaluate("SOX", ctx));

        assertThat(result.compliant())
                .as("entity satisfying all SOX rules must be compliant")
                .isTrue();
    }

    // ── KP-CPL-003: HIPAA deterministic fixture ───────────────────────────────

    @Test
    @DisplayName("KP-CPL-003: PHI access without authentication must violate HIPAA-001")
    void testHipaaPhiAccessWithoutAuth() {
        CompliancePlugin.ComplianceContext ctx = new CompliancePlugin.ComplianceContext(
                "phi-record-" + UUID.randomUUID(), "PHI_RECORD",
                Map.of("need_to_know", true),  // missing authentication_required
                "unauth-user", Instant.now());

        CompliancePlugin.ComplianceResult result = runPromise(() -> plugin.evaluate("HIPAA", ctx));

        assertThat(result.compliant()).isFalse();
        assertThat(result.violations())
                .as("unauthenticated PHI access must produce HIPAA-001 violation")
                .anyMatch(v -> v.ruleId().equals("HIPAA-001"));
    }

    // ── KP-CPL-004: Audit trail isolation ────────────────────────────────────

    @Test
    @DisplayName("KP-CPL-004: audit trail for entity-A must not contain entity-B entries")
    void testAuditTrailIsolation() {
        String entityA = "entity-A-" + UUID.randomUUID();
        String entityB = "entity-B-" + UUID.randomUUID();

        CompliancePlugin.ComplianceContext ctxA = new CompliancePlugin.ComplianceContext(
                entityA, "TRADE", Map.of("audit_required", true, "approval_required", true),
                "user-a", Instant.now());
        CompliancePlugin.ComplianceContext ctxB = new CompliancePlugin.ComplianceContext(
                entityB, "TRADE", Map.of("audit_required", true, "approval_required", true),
                "user-b", Instant.now());

        runPromise(() -> plugin.evaluate("SOX", ctxA));
        runPromise(() -> plugin.evaluate("SOX", ctxB));

        List<CompliancePlugin.AuditEntry> trailA = runPromise(() -> plugin.getAuditTrail(entityA));
        List<CompliancePlugin.AuditEntry> trailB = runPromise(() -> plugin.getAuditTrail(entityB));

        assertThat(trailA)
                .as("entity-A audit trail must not be empty")
                .isNotEmpty();
        assertThat(trailA)
                .as("entity-A audit trail must not contain entity-B entries")
                .noneMatch(e -> e.entityId().equals(entityB));
        assertThat(trailB)
                .as("entity-B audit trail must not contain entity-A entries")
                .noneMatch(e -> e.entityId().equals(entityA));
    }

    // ── KP-CPL-005: Violation isolation ──────────────────────────────────────

    @Test
    @DisplayName("KP-CPL-005: getActiveViolations for finance-ruleset must not include HIPAA violations")
    void testActiveViolationIsolation() {
        // Cause a SOX violation
        CompliancePlugin.ComplianceContext finCtx = new CompliancePlugin.ComplianceContext(
                "fin-entity-" + UUID.randomUUID(), "TRADE",
                Map.of("amount", 5000),
                "trader", Instant.now());
        runPromise(() -> plugin.evaluate("SOX", finCtx));

        // Cause a HIPAA violation
        CompliancePlugin.ComplianceContext phrCtx = new CompliancePlugin.ComplianceContext(
                "phi-entity-" + UUID.randomUUID(), "PHI_RECORD",
                Map.of(),
                "nurse", Instant.now());
        runPromise(() -> plugin.evaluate("HIPAA", phrCtx));

        List<CompliancePlugin.ComplianceViolation> soxViolations =
                runPromise(() -> plugin.getActiveViolations("SOX"));
        List<CompliancePlugin.ComplianceViolation> hipaaViolations =
                runPromise(() -> plugin.getActiveViolations("HIPAA"));

        assertThat(soxViolations)
                .as("SOX active violations must not include HIPAA-prefixed rules")
                .noneMatch(v -> v.ruleId().startsWith("HIPAA-"));
        assertThat(hipaaViolations)
                .as("HIPAA active violations must not include SOX-prefixed rules")
                .noneMatch(v -> v.ruleId().startsWith("SOX-"));
    }
}
