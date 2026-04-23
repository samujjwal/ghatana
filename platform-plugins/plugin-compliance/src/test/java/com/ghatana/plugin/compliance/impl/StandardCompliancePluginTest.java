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
@ExtendWith(MockitoExtension.class) // GH-90000
class StandardCompliancePluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardCompliancePlugin compliancePlugin;

    @BeforeEach
    void setUp() { // GH-90000
        compliancePlugin = new StandardCompliancePlugin(); // GH-90000
    }

    @Test
    @DisplayName("Should initialize compliance plugin")
    void testInitialize() { // GH-90000
        assertThat(compliancePlugin.getState()).isEqualTo(PluginState.UNLOADED); // GH-90000
        Promise<Void> result = compliancePlugin.initialize(mockContext); // GH-90000
        runPromise(() -> result); // GH-90000
        assertThat(compliancePlugin.getState()).isEqualTo(PluginState.INITIALIZED); // GH-90000
    }

    @Test
    @DisplayName("Should start compliance plugin")
    void testStart() { // GH-90000
        runPromise(() -> compliancePlugin.initialize(mockContext)); // GH-90000
        Promise<Void> result = compliancePlugin.start(); // GH-90000
        runPromise(() -> result); // GH-90000
        assertThat(compliancePlugin.getState()).isEqualTo(PluginState.RUNNING); // GH-90000
    }

    @Test
    @DisplayName("Should return correct metadata")
    void testMetadata() { // GH-90000
        var metadata = compliancePlugin.metadata(); // GH-90000
        assertThat(metadata.name()).isEqualTo("Compliance Plugin");
        assertThat(metadata.version()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("Should evaluate compliance against SOX rules")
    void testEvaluateCompliance_SOX() { // GH-90000
        runPromise(() -> compliancePlugin.initialize(mockContext) // GH-90000
                .then(v -> compliancePlugin.start())); // GH-90000

        CompliancePlugin.ComplianceContext context = new CompliancePlugin.ComplianceContext( // GH-90000
            "entity1", "TRANSACTION", Map.of("approval", "true"), // GH-90000
            "user1", Instant.now()); // GH-90000

        Promise<CompliancePlugin.ComplianceResult> result =
                compliancePlugin.evaluate("SOX", context); // GH-90000
        CompliancePlugin.ComplianceResult evaluation = runPromise(() -> result); // GH-90000

        assertThat(evaluation.ruleSetId()).isEqualTo("SOX");
        assertThat(evaluation.evaluatedAt()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should evaluate compliance against HIPAA rules")
    void testEvaluateCompliance_HIPAA() { // GH-90000
        runPromise(() -> compliancePlugin.initialize(mockContext) // GH-90000
                .then(v -> compliancePlugin.start())); // GH-90000

        CompliancePlugin.ComplianceContext context = new CompliancePlugin.ComplianceContext( // GH-90000
            "patient1", "PHI_ACCESS", Map.of("auth", "token"), // GH-90000
            "doctor1", Instant.now()); // GH-90000

        Promise<CompliancePlugin.ComplianceResult> result =
                compliancePlugin.evaluate("HIPAA", context); // GH-90000
        CompliancePlugin.ComplianceResult evaluation = runPromise(() -> result); // GH-90000

        assertThat(evaluation.ruleSetId()).isEqualTo("HIPAA");
    }

    @Test
    @DisplayName("Should register custom rules")
    void testRegisterRuleSet() { // GH-90000
        runPromise(() -> compliancePlugin.initialize(mockContext) // GH-90000
                .then(v -> compliancePlugin.start())); // GH-90000

        List<CompliancePlugin.ComplianceRule> customRules = Arrays.asList( // GH-90000
            new CompliancePlugin.ComplianceRule("CUSTOM-001", "CUSTOM", "Custom rule", // GH-90000
                CompliancePlugin.ComplianceRule.Severity.HIGH, "custom_check")
        );

        Promise<Void> result = compliancePlugin.registerRuleSet("CUSTOM", customRules); // GH-90000
        runPromise(() -> result); // GH-90000

        // Verify rule was registered by evaluating
        CompliancePlugin.ComplianceContext context = new CompliancePlugin.ComplianceContext( // GH-90000
            "entity1", "TYPE", new java.util.HashMap<>(), "user1", Instant.now()); // GH-90000

        Promise<CompliancePlugin.ComplianceResult> evalResult =
                compliancePlugin.evaluate("CUSTOM", context); // GH-90000
        CompliancePlugin.ComplianceResult evaluation = runPromise(() -> evalResult); // GH-90000

        assertThat(evaluation.ruleSetId()).isEqualTo("CUSTOM");
    }

    @Test
    @DisplayName("Should get audit trail for entity")
    void testGetAuditTrail() { // GH-90000
        runPromise(() -> compliancePlugin.initialize(mockContext) // GH-90000
                .then(v -> compliancePlugin.start())); // GH-90000

        String entityId = "audit_entity";
        CompliancePlugin.ComplianceContext context = new CompliancePlugin.ComplianceContext( // GH-90000
            entityId, "TYPE", new java.util.HashMap<>(), "user1", Instant.now()); // GH-90000

        runPromise(() -> compliancePlugin.evaluate("GDPR", context)); // GH-90000

        Promise<List<CompliancePlugin.AuditEntry>> result =
                compliancePlugin.getAuditTrail(entityId); // GH-90000
        List<CompliancePlugin.AuditEntry> trail = runPromise(() -> result); // GH-90000

        assertThat(trail).isNotEmpty(); // GH-90000
        assertThat(trail.get(0).entityId()).isEqualTo(entityId); // GH-90000
    }

    @Test
    @DisplayName("Should track active violations")
    void testGetActiveViolations() { // GH-90000
        runPromise(() -> compliancePlugin.initialize(mockContext) // GH-90000
                .then(v -> compliancePlugin.start())); // GH-90000

        Promise<List<CompliancePlugin.ComplianceViolation>> result =
                compliancePlugin.getActiveViolations("SOX");
        List<CompliancePlugin.ComplianceViolation> violations = runPromise(() -> result); // GH-90000

        assertThat(violations).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should shutdown compliance plugin")
    void testShutdown() { // GH-90000
        runPromise(() -> compliancePlugin.initialize(mockContext) // GH-90000
                .then(v -> compliancePlugin.start())); // GH-90000

        Promise<Void> result = compliancePlugin.shutdown(); // GH-90000
        runPromise(() -> result); // GH-90000

        assertThat(compliancePlugin.getState()).isEqualTo(PluginState.UNLOADED); // GH-90000
    }
}
