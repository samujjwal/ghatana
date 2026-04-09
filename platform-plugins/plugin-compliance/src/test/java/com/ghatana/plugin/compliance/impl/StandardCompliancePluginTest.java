package com.ghatana.plugin.compliance.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.compliance.CompliancePlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Comprehensive tests for StandardCompliancePlugin
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("StandardCompliancePlugin Tests")
@ExtendWith(MockitoExtension.class)
class StandardCompliancePluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardCompliancePlugin compliancePlugin;

    @BeforeEach
    void setUp() {
        compliancePlugin = new StandardCompliancePlugin();
    }

    @Test
    @DisplayName("Should initialize compliance plugin")
    void testInitialize() {
        assertThat(compliancePlugin.getState()).isEqualTo(PluginState.UNLOADED);
        Promise<Void> result = compliancePlugin.initialize(mockContext);
        runPromise(() -> result);
        assertThat(compliancePlugin.getState()).isEqualTo(PluginState.INITIALIZED);
    }

    @Test
    @DisplayName("Should start compliance plugin")
    void testStart() {
        runPromise(() -> compliancePlugin.initialize(mockContext));
        Promise<Void> result = compliancePlugin.start();
        runPromise(() -> result);
        assertThat(compliancePlugin.getState()).isEqualTo(PluginState.RUNNING);
    }

    @Test
    @DisplayName("Should return correct metadata")
    void testMetadata() {
        var metadata = compliancePlugin.metadata();
        assertThat(metadata.name()).isEqualTo("Compliance Plugin");
        assertThat(metadata.version()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("Should evaluate compliance against SOX rules")
    void testEvaluateCompliance_SOX() {
        runPromise(() -> compliancePlugin.initialize(mockContext)
                .then(v -> compliancePlugin.start()));

        CompliancePlugin.ComplianceContext context = new CompliancePlugin.ComplianceContext(
            "entity1", "TRANSACTION", Map.of("approval", "true"),
            "user1", Instant.now());

        Promise<CompliancePlugin.ComplianceResult> result =
                compliancePlugin.evaluate("SOX", context);
        CompliancePlugin.ComplianceResult evaluation = runPromise(() -> result);

        assertThat(evaluation.ruleSetId()).isEqualTo("SOX");
        assertThat(evaluation.evaluatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should evaluate compliance against HIPAA rules")
    void testEvaluateCompliance_HIPAA() {
        runPromise(() -> compliancePlugin.initialize(mockContext)
                .then(v -> compliancePlugin.start()));

        CompliancePlugin.ComplianceContext context = new CompliancePlugin.ComplianceContext(
            "patient1", "PHI_ACCESS", Map.of("auth", "token"),
            "doctor1", Instant.now());

        Promise<CompliancePlugin.ComplianceResult> result =
                compliancePlugin.evaluate("HIPAA", context);
        CompliancePlugin.ComplianceResult evaluation = runPromise(() -> result);

        assertThat(evaluation.ruleSetId()).isEqualTo("HIPAA");
    }

    @Test
    @DisplayName("Should register custom rules")
    void testRegisterRuleSet() {
        runPromise(() -> compliancePlugin.initialize(mockContext)
                .then(v -> compliancePlugin.start()));

        List<CompliancePlugin.ComplianceRule> customRules = Arrays.asList(
            new CompliancePlugin.ComplianceRule("CUSTOM-001", "CUSTOM", "Custom rule",
                CompliancePlugin.ComplianceRule.Severity.HIGH, "custom_check")
        );

        Promise<Void> result = compliancePlugin.registerRuleSet("CUSTOM", customRules);
        runPromise(() -> result);

        // Verify rule was registered by evaluating
        CompliancePlugin.ComplianceContext context = new CompliancePlugin.ComplianceContext(
            "entity1", "TYPE", new java.util.HashMap<>(), "user1", Instant.now());

        Promise<CompliancePlugin.ComplianceResult> evalResult =
                compliancePlugin.evaluate("CUSTOM", context);
        CompliancePlugin.ComplianceResult evaluation = runPromise(() -> evalResult);

        assertThat(evaluation.ruleSetId()).isEqualTo("CUSTOM");
    }

    @Test
    @DisplayName("Should get audit trail for entity")
    void testGetAuditTrail() {
        runPromise(() -> compliancePlugin.initialize(mockContext)
                .then(v -> compliancePlugin.start()));

        String entityId = "audit_entity";
        CompliancePlugin.ComplianceContext context = new CompliancePlugin.ComplianceContext(
            entityId, "TYPE", new java.util.HashMap<>(), "user1", Instant.now());

        runPromise(() -> compliancePlugin.evaluate("GDPR", context));

        Promise<List<CompliancePlugin.AuditEntry>> result =
                compliancePlugin.getAuditTrail(entityId);
        List<CompliancePlugin.AuditEntry> trail = runPromise(() -> result);

        assertThat(trail).isNotEmpty();
        assertThat(trail.get(0).entityId()).isEqualTo(entityId);
    }

    @Test
    @DisplayName("Should track active violations")
    void testGetActiveViolations() {
        runPromise(() -> compliancePlugin.initialize(mockContext)
                .then(v -> compliancePlugin.start()));

        Promise<List<CompliancePlugin.ComplianceViolation>> result =
                compliancePlugin.getActiveViolations("SOX");
        List<CompliancePlugin.ComplianceViolation> violations = runPromise(() -> result);

        assertThat(violations).isNotNull();
    }

    @Test
    @DisplayName("Should shutdown compliance plugin")
    void testShutdown() {
        runPromise(() -> compliancePlugin.initialize(mockContext)
                .then(v -> compliancePlugin.start()));

        Promise<Void> result = compliancePlugin.shutdown();
        runPromise(() -> result);

        assertThat(compliancePlugin.getState()).isEqualTo(PluginState.UNLOADED);
    }
}
