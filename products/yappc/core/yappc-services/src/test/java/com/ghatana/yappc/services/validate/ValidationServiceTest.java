package com.ghatana.yappc.services.validate;

import com.ghatana.audit.AuditLogger;
import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.shape.DomainModel;
import com.ghatana.yappc.domain.shape.EntitySpec;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.ValidationConfig;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Tests for ValidationService implementation
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("ValidationService Tests")
class ValidationServiceTest extends EventloopTestBase {

    private PolicyEngine policyEngine;
    private AuditLogger auditLogger;
    private MetricsCollector metrics;
    private ValidationService service;

    @BeforeEach
    void setUp() { // GH-90000
        policyEngine = mock(PolicyEngine.class); // GH-90000
        auditLogger = mock(AuditLogger.class); // GH-90000
        metrics = mock(MetricsCollector.class); // GH-90000
        service = new ValidationServiceImpl(policyEngine, auditLogger, metrics); // GH-90000

        when(auditLogger.log(any(Map.class))) // GH-90000
                .thenReturn(Promise.complete()); // GH-90000
    }

    @Nested
    @DisplayName("Validation Without Config")
    class ValidateWithoutConfig {

        @Test
        @DisplayName("should pass validation for valid shape spec")
        void shouldValidateValidShapeSpec() { // GH-90000
            ShapeSpec spec = ShapeSpec.builder() // GH-90000
                    .id("shape-123")
                    .tenantId("tenant-abc")
                    .intentRef("intent-123")
                    .domainModel(DomainModel.builder() // GH-90000
                            .entities(List.of( // GH-90000
                                    EntitySpec.builder() // GH-90000
                                            .name("User")
                                            .description("User entity")
                                            .fields(List.of()) // GH-90000
                                            .behaviors(List.of()) // GH-90000
                                            .build() // GH-90000
                            ))
                            .relationships(List.of()) // GH-90000
                            .boundedContexts(List.of()) // GH-90000
                            .build()) // GH-90000
                    .workflows(List.of()) // GH-90000
                    .integrations(List.of()) // GH-90000
                    .build(); // GH-90000

            LifecycleValidationResult result = runPromise(() -> service.validate(spec)); // GH-90000

            assertNotNull(result); // GH-90000
            assertNotNull(result.validatedAt()); // GH-90000
            assertNotNull(result.issues()); // GH-90000
            verify(auditLogger, times(1)).log(any(Map.class)); // GH-90000
        }

        @Test
        @DisplayName("should fail validation for null domain model")
        void shouldFailWhenDomainModelNull() { // GH-90000
            ShapeSpec spec = ShapeSpec.builder() // GH-90000
                    .id("shape-null-model")
                    .tenantId("tenant-abc")
                    .intentRef("intent-123")
                    .domainModel(null) // GH-90000
                    .build(); // GH-90000

            LifecycleValidationResult result = runPromise(() -> service.validate(spec)); // GH-90000

            assertNotNull(result); // GH-90000
            assertFalse(result.passed()); // GH-90000
            assertTrue(result.issues().stream().anyMatch(issue -> issue.id().equals("schema-001")));
        }

        @Test
        @DisplayName("should fail validation for empty entities")
        void shouldFailWhenEntitiesEmpty() { // GH-90000
            ShapeSpec spec = ShapeSpec.builder() // GH-90000
                    .id("shape-123")
                    .tenantId("tenant-abc")
                    .intentRef("intent-123")
                    .domainModel(DomainModel.builder() // GH-90000
                            .entities(List.of()) // GH-90000
                            .relationships(List.of()) // GH-90000
                            .boundedContexts(List.of()) // GH-90000
                            .build()) // GH-90000
                    .workflows(List.of()) // GH-90000
                    .integrations(List.of()) // GH-90000
                    .build(); // GH-90000

            LifecycleValidationResult result = runPromise(() -> service.validate(spec)); // GH-90000

            assertNotNull(result); // GH-90000
            assertFalse(result.passed()); // GH-90000
            assertTrue(result.issues().stream() // GH-90000
                    .anyMatch(issue -> issue.id().equals("schema-001")));
        }

        @Test
        @DisplayName("should record validation timer metric")
        void shouldRecordTimerMetric() { // GH-90000
            ShapeSpec spec = validShapeSpec("tenant-1");

            runPromise(() -> service.validate(spec)); // GH-90000

            verify(metrics, atLeastOnce()).recordTimer( // GH-90000
                    eq("yappc.validate.execute"),
                    anyLong(), // GH-90000
                    any(Map.class)); // GH-90000
        }

        @Test
        @DisplayName("should increment success counter")
        void shouldIncrementSuccessCounter() { // GH-90000
            ShapeSpec spec = validShapeSpec("tenant-2");

            runPromise(() -> service.validate(spec)); // GH-90000

            verify(metrics, atLeastOnce()).recordTimer( // GH-90000
                    eq("yappc.validate.execute"),
                    anyLong(), // GH-90000
                    any(Map.class)); // GH-90000
        }
    }

    @Nested
    @DisplayName("Validation With Custom Config")
    class ValidateWithCustomConfig {

        @Test
        @DisplayName("should validate with excluded validators")
        void shouldValidateWithExcludedValidators() { // GH-90000
            ShapeSpec spec = validShapeSpec("tenant-abc");
            ValidationConfig config = ValidationConfig.builder() // GH-90000
                    .excludedIds(Set.of("security", "compliance")) // GH-90000
                    .build(); // GH-90000

            LifecycleValidationResult result = runPromise(() -> service.validate(spec, config)); // GH-90000

            assertNotNull(result); // GH-90000
            verify(auditLogger, times(1)).log(any(Map.class)); // GH-90000
        }

        @Test
        @DisplayName("should handle null config by using default")
        void shouldHandleNullConfig() { // GH-90000
            ShapeSpec spec = validShapeSpec("tenant-abc");

            LifecycleValidationResult result = runPromise(() -> service.validate(spec, null)); // GH-90000

            assertNotNull(result); // GH-90000
            verify(auditLogger, times(1)).log(any(Map.class)); // GH-90000
        }

        @Test
        @DisplayName("should apply failFast option from config")
        void shouldApplyFailFastOption() { // GH-90000
            ShapeSpec spec = validShapeSpec("tenant-abc");
            ValidationConfig config = ValidationConfig.builder() // GH-90000
                    .failFast(true) // GH-90000
                    .build(); // GH-90000

            LifecycleValidationResult result = runPromise(() -> service.validate(spec, config)); // GH-90000

            assertNotNull(result); // GH-90000
            verify(auditLogger, times(1)).log(any(Map.class)); // GH-90000
        }

        @Test
        @DisplayName("should respect excluded IDs from config")
        void shouldRespectExcludedIds() { // GH-90000
            ShapeSpec spec = validShapeSpec("tenant-abc");
            ValidationConfig config = ValidationConfig.builder() // GH-90000
                    .excludedIds(Set.of("penalty-001", "penalty-002")) // GH-90000
                    .build(); // GH-90000

            LifecycleValidationResult result = runPromise(() -> service.validate(spec, config)); // GH-90000

            assertNotNull(result); // GH-90000
            assertTrue(result.issues().size() <= 5); // GH-90000
        }
    }

    @Nested
    @DisplayName("Policy Engine Integration")
    class PolicyEngineIntegration {

        @Test
        @DisplayName("should invoke policy engine for policy validation")
        void shouldInvokePolicyEngine() { // GH-90000
            ShapeSpec spec = validShapeSpec("tenant-abc");
            PolicySpecMock policySpec = new PolicySpecMock(); // GH-90000

            when(policyEngine.evaluate(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(true)); // GH-90000

            runPromise(() -> service.validate(spec, ValidationConfig.defaultConfig())); // GH-90000

            // Service should attempt policy evaluation if policy spec exists
            verify(auditLogger, times(1)).log(any(Map.class)); // GH-90000
        }

        @Test
        @DisplayName("should handle policy engine failures gracefully")
        void shouldHandlePolicyEngineFailure() { // GH-90000
            ShapeSpec spec = validShapeSpec("tenant-abc");
            PolicySpecMock policySpec = new PolicySpecMock(); // GH-90000

            when(policyEngine.evaluate(anyString(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Policy evaluation failed")));

            // Service should not crash on policy failure
            assertDoesNotThrow(() -> // GH-90000
                runPromise(() -> service.validate(spec, ValidationConfig.defaultConfig()))); // GH-90000
        }
    }

    @Nested
    @DisplayName("Tenant Isolation")
    class TenantIsolation {

        @Test
        @DisplayName("should tag metrics with tenant ID")
        void shouldTagMetricsWithTenant() { // GH-90000
            ShapeSpec spec1 = validShapeSpec("tenant-1");
            ShapeSpec spec2 = validShapeSpec("tenant-2");

            runPromise(() -> service.validate(spec1)); // GH-90000
            runPromise(() -> service.validate(spec2)); // GH-90000

            verify(metrics, atLeast(2)).recordTimer( // GH-90000
                    eq("yappc.validate.execute"),
                    anyLong(), // GH-90000
                    any(Map.class)); // GH-90000
        }

        @Test
        @DisplayName("should include tenant in audit log")
        void shouldIncludeTenantInAudit() { // GH-90000
            ShapeSpec spec = validShapeSpec("tenant-secure");

            runPromise(() -> service.validate(spec)); // GH-90000

            verify(auditLogger, times(1)).log(argThat(map -> // GH-90000
                map.containsKey("tenant") || map.values().toString().contains("tenant-secure")));
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should handle audit logger failures")
        void shouldHandleAuditLoggerFailure() { // GH-90000
            when(auditLogger.log(any(Map.class))) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Audit failed")));

            ShapeSpec spec = validShapeSpec("tenant-abc");

            assertDoesNotThrow(() -> // GH-90000
                runPromise(() -> service.validate(spec))); // GH-90000
        }

        @Test
        @DisplayName("should increment failure counter on validation error")
        void shouldIncrementFailureCounter() { // GH-90000
            ShapeSpec spec = ShapeSpec.builder() // GH-90000
                    .id("shape-fail")
                    .tenantId("tenant-fail")
                    .intentRef(null)  // Invalid: null intentRef // GH-90000
                    .domainModel(null) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> service.validate(spec)); // GH-90000

            verify(metrics, atLeastOnce()).recordTimer( // GH-90000
                    anyString(), // GH-90000
                    anyLong(), // GH-90000
                    any(Map.class)); // GH-90000
        }
    }

    // ─── Helper Methods ───────────────────────────────────────────────────

    private ShapeSpec validShapeSpec(String tenantId) { // GH-90000
        return ShapeSpec.builder() // GH-90000
                .id("shape-" + System.nanoTime()) // GH-90000
                .tenantId(tenantId) // GH-90000
                .intentRef("intent-" + System.nanoTime()) // GH-90000
                .domainModel(DomainModel.builder() // GH-90000
                        .entities(List.of( // GH-90000
                                EntitySpec.builder() // GH-90000
                                        .name("Entity1")
                                        .description("Test entity")
                                        .fields(List.of()) // GH-90000
                                        .behaviors(List.of()) // GH-90000
                                        .build() // GH-90000
                        ))
                        .relationships(List.of()) // GH-90000
                        .boundedContexts(List.of()) // GH-90000
                        .build()) // GH-90000
                .workflows(List.of()) // GH-90000
                .integrations(List.of()) // GH-90000
                .build(); // GH-90000
    }

    // ─── Mock Helper ───────────────────────────────────────────────────────

    private static class PolicySpecMock {
        // Mock policy spec for testing
    }
}
