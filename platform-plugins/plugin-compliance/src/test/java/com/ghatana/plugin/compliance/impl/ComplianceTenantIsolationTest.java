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
 *   <li>KP-CPL-002: AUDIT_CONTROL rule pack — entities missing audit flag must be non-compliant</li>
 *   <li>KP-CPL-003: ACCESS_CONTROL rule pack — entities without authentication must violate AC-010</li>
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
        // Register domain-agnostic rule packs used by fixture tests
        runPromise(() -> plugin.registerRuleSet("AUDIT_CONTROL", List.of(
            new CompliancePlugin.ComplianceRule("AC-001", "SEPARATION_OF_DUTIES",
                "Operations require dual approval", CompliancePlugin.ComplianceRule.Severity.HIGH, "approval_required"),
            new CompliancePlugin.ComplianceRule("AC-002", "AUDIT_TRAIL",
                "All changes must be logged", CompliancePlugin.ComplianceRule.Severity.CRITICAL, "audit_required")
        )));
        runPromise(() -> plugin.registerRuleSet("ACCESS_CONTROL", List.of(
            new CompliancePlugin.ComplianceRule("AC-010", "AUTHENTICATION_REQUIRED",
                "Resource access requires authentication", CompliancePlugin.ComplianceRule.Severity.CRITICAL, "authentication_required"),
            new CompliancePlugin.ComplianceRule("AC-011", "NEED_TO_KNOW",
                "Access limited to authorised scope", CompliancePlugin.ComplianceRule.Severity.HIGH, "need_to_know")
        )));
    }

    // ── KP-CPL-001: Rule set isolation ───────────────────────────────────────

    @Test
    @DisplayName("KP-CPL-001: custom rule set for domain-a must not interfere with domain-b rule set")
    void testRuleSetIsolation() {
        // Register a blocking rule only for domain-a
        CompliancePlugin.ComplianceRule blockAll = new CompliancePlugin.ComplianceRule(
                "DOM-BLOCK-ALL", "BLOCK_ALWAYS", "Always block for testing",
                CompliancePlugin.ComplianceRule.Severity.CRITICAL, "block");
        runPromise(() -> plugin.registerRuleSet("domain-a-ruleset",
                List.of(blockAll)));

        CompliancePlugin.ComplianceContext domainACtx = new CompliancePlugin.ComplianceContext(
                "entity-001", "OPERATION", Map.of(), "user-a", Instant.now());
        CompliancePlugin.ComplianceContext domainBCtx = new CompliancePlugin.ComplianceContext(
                "entity-002", "OPERATION", Map.of("authenticated", true), "user-b", Instant.now());

        CompliancePlugin.ComplianceResult domainAResult = runPromise(() -> plugin.evaluate("domain-a-ruleset", domainACtx));
        CompliancePlugin.ComplianceResult domainBResult = runPromise(() -> plugin.evaluate("ACCESS_CONTROL", domainBCtx));

        assertThat(domainAResult.compliant())
                .as("domain-a rule set should fail due to blocking rule")
                .isFalse();
        // domain-b context evaluated against ACCESS_CONTROL — must not be contaminated by domain-a rule
        assertThat(domainBResult.violations())
                .as("domain-b evaluation must not contain domain-a-specific violations")
                .noneMatch(v -> v.ruleId().startsWith("DOM-"));
    }

    // ── KP-CPL-002: AUDIT_CONTROL deterministic fixture ──────────────────────

    @Test
    @DisplayName("KP-CPL-002: entity missing 'audit_required' flag must violate AC-002")
    void testAuditRequiredViolation() {
        CompliancePlugin.ComplianceContext ctx = new CompliancePlugin.ComplianceContext(
                "txn-" + UUID.randomUUID(), "FINANCIAL_TRANSACTION",
                Map.of("amount", 10_000, "approval_required", true),
                "user-1", Instant.now());

        CompliancePlugin.ComplianceResult result = runPromise(() -> plugin.evaluate("AUDIT_CONTROL", ctx));

        assertThat(result.compliant()).isFalse();
        assertThat(result.violations())
                .as("missing audit flag must produce AC-002 violation")
                .anyMatch(v -> v.ruleId().equals("AC-002"));
    }

    @Test
    @DisplayName("KP-CPL-002: entity with both audit and approval flags must be AUDIT_CONTROL compliant")
    void testCompliantEntity() {
        CompliancePlugin.ComplianceContext ctx = new CompliancePlugin.ComplianceContext(
                "txn-" + UUID.randomUUID(), "FINANCIAL_TRANSACTION",
                Map.of("amount", 10_000, "approval_required", true, "audit_required", true),
                "user-1", Instant.now());

        CompliancePlugin.ComplianceResult result = runPromise(() -> plugin.evaluate("AUDIT_CONTROL", ctx));

        assertThat(result.compliant())
                .as("entity satisfying all AUDIT_CONTROL rules must be compliant")
                .isTrue();
    }

    // ── KP-CPL-003: ACCESS_CONTROL deterministic fixture ─────────────────────

    @Test
    @DisplayName("KP-CPL-003: resource access without authentication must violate AC-010")
    void testAccessWithoutAuth() {
        CompliancePlugin.ComplianceContext ctx = new CompliancePlugin.ComplianceContext(
                "resource-" + UUID.randomUUID(), "RESOURCE_ACCESS",
                Map.of("need_to_know", true),  // missing authentication_required
                "unauth-user", Instant.now());

        CompliancePlugin.ComplianceResult result = runPromise(() -> plugin.evaluate("ACCESS_CONTROL", ctx));

        assertThat(result.compliant()).isFalse();
        assertThat(result.violations())
                .as("unauthenticated access must produce AC-010 violation")
                .anyMatch(v -> v.ruleId().equals("AC-010"));
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

        runPromise(() -> plugin.evaluate("AUDIT_CONTROL", ctxA));
        runPromise(() -> plugin.evaluate("AUDIT_CONTROL", ctxB));

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
    @DisplayName("KP-CPL-005: getActiveViolations for AUDIT_CONTROL must not include ACCESS_CONTROL violations")
    void testActiveViolationIsolation() {
        // Cause an AUDIT_CONTROL violation
        CompliancePlugin.ComplianceContext auditCtx = new CompliancePlugin.ComplianceContext(
                "entity-" + UUID.randomUUID(), "OPERATION",
                Map.of("amount", 5000),
                "user-a", Instant.now());
        runPromise(() -> plugin.evaluate("AUDIT_CONTROL", auditCtx));

        // Cause an ACCESS_CONTROL violation
        CompliancePlugin.ComplianceContext accessCtx = new CompliancePlugin.ComplianceContext(
                "entity-" + UUID.randomUUID(), "RESOURCE_ACCESS",
                Map.of(),
                "user-b", Instant.now());
        runPromise(() -> plugin.evaluate("ACCESS_CONTROL", accessCtx));

        List<CompliancePlugin.ComplianceViolation> auditViolations =
                runPromise(() -> plugin.getActiveViolations("AUDIT_CONTROL"));
        List<CompliancePlugin.ComplianceViolation> accessViolations =
                runPromise(() -> plugin.getActiveViolations("ACCESS_CONTROL"));

        assertThat(auditViolations)
                .as("AUDIT_CONTROL active violations must not include AC-010+ (access) rule IDs")
                .noneMatch(v -> v.ruleId().startsWith("AC-01"));
        assertThat(accessViolations)
                .as("ACCESS_CONTROL violations must not include AC-001/AC-002 (audit) rule IDs")
                .noneMatch(v -> v.ruleId().equals("AC-001") || v.ruleId().equals("AC-002"));
    }
}
