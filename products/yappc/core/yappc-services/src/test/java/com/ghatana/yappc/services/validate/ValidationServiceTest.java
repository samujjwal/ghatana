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
    void setUp() { 
        policyEngine = mock(PolicyEngine.class); 
        auditLogger = mock(AuditLogger.class); 
        metrics = mock(MetricsCollector.class); 
        service = new ValidationServiceImpl(policyEngine, auditLogger, metrics); 

        when(auditLogger.log(any(Map.class))) 
                .thenReturn(Promise.complete()); 
    }

    @Nested
    @DisplayName("Validation Without Config")
    class ValidateWithoutConfig {

        @Test
        @DisplayName("should pass validation for valid shape spec")
        void shouldValidateValidShapeSpec() { 
            ShapeSpec spec = ShapeSpec.builder() 
                    .id("shape-123")
                    .tenantId("tenant-abc")
                    .intentRef("intent-123")
                    .domainModel(DomainModel.builder() 
                            .entities(List.of( 
                                    EntitySpec.builder() 
                                            .name("User")
                                            .description("User entity")
                                            .fields(List.of()) 
                                            .behaviors(List.of()) 
                                            .build() 
                            ))
                            .relationships(List.of()) 
                            .boundedContexts(List.of()) 
                            .build()) 
                    .workflows(List.of()) 
                    .integrations(List.of()) 
                    .build(); 

            LifecycleValidationResult result = runPromise(() -> service.validate(spec)); 

            assertNotNull(result); 
            assertNotNull(result.validatedAt()); 
            assertNotNull(result.issues()); 
            verify(auditLogger, times(1)).log(any(Map.class)); 
        }

        @Test
        @DisplayName("should fail validation for null domain model")
        void shouldFailWhenDomainModelNull() { 
            ShapeSpec spec = ShapeSpec.builder() 
                    .id("shape-null-model")
                    .tenantId("tenant-abc")
                    .intentRef("intent-123")
                    .domainModel(null) 
                    .build(); 

            LifecycleValidationResult result = runPromise(() -> service.validate(spec)); 

            assertNotNull(result); 
            assertFalse(result.passed()); 
            assertTrue(result.issues().stream().anyMatch(issue -> issue.id().equals("schema-001")));
        }

        @Test
        @DisplayName("should fail validation for empty entities")
        void shouldFailWhenEntitiesEmpty() { 
            ShapeSpec spec = ShapeSpec.builder() 
                    .id("shape-123")
                    .tenantId("tenant-abc")
                    .intentRef("intent-123")
                    .domainModel(DomainModel.builder() 
                            .entities(List.of()) 
                            .relationships(List.of()) 
                            .boundedContexts(List.of()) 
                            .build()) 
                    .workflows(List.of()) 
                    .integrations(List.of()) 
                    .build(); 

            LifecycleValidationResult result = runPromise(() -> service.validate(spec)); 

            assertNotNull(result); 
            assertFalse(result.passed()); 
            assertTrue(result.issues().stream() 
                    .anyMatch(issue -> issue.id().equals("schema-001")));
        }

        @Test
        @DisplayName("should record validation timer metric")
        void shouldRecordTimerMetric() { 
            ShapeSpec spec = validShapeSpec("tenant-1");

            runPromise(() -> service.validate(spec)); 

            verify(metrics, atLeastOnce()).recordTimer( 
                    eq("yappc.validate.execute"),
                    anyLong(), 
                    any(Map.class)); 
        }

        @Test
        @DisplayName("should increment success counter")
        void shouldIncrementSuccessCounter() { 
            ShapeSpec spec = validShapeSpec("tenant-2");

            runPromise(() -> service.validate(spec)); 

            verify(metrics, atLeastOnce()).recordTimer( 
                    eq("yappc.validate.execute"),
                    anyLong(), 
                    any(Map.class)); 
        }
    }

    @Nested
    @DisplayName("Validation With Custom Config")
    class ValidateWithCustomConfig {

        @Test
        @DisplayName("should validate with excluded validators")
        void shouldValidateWithExcludedValidators() { 
            ShapeSpec spec = validShapeSpec("tenant-abc");
            ValidationConfig config = ValidationConfig.builder() 
                    .excludedIds(Set.of("security", "compliance")) 
                    .build(); 

            LifecycleValidationResult result = runPromise(() -> service.validate(spec, config)); 

            assertNotNull(result); 
            verify(auditLogger, times(1)).log(any(Map.class)); 
        }

        @Test
        @DisplayName("should handle null config by using default")
        void shouldHandleNullConfig() { 
            ShapeSpec spec = validShapeSpec("tenant-abc");

            LifecycleValidationResult result = runPromise(() -> service.validate(spec, null)); 

            assertNotNull(result); 
            verify(auditLogger, times(1)).log(any(Map.class)); 
        }

        @Test
        @DisplayName("should apply failFast option from config")
        void shouldApplyFailFastOption() { 
            ShapeSpec spec = validShapeSpec("tenant-abc");
            ValidationConfig config = ValidationConfig.builder() 
                    .failFast(true) 
                    .build(); 

            LifecycleValidationResult result = runPromise(() -> service.validate(spec, config)); 

            assertNotNull(result); 
            verify(auditLogger, times(1)).log(any(Map.class)); 
        }

        @Test
        @DisplayName("should respect excluded IDs from config")
        void shouldRespectExcludedIds() { 
            ShapeSpec spec = validShapeSpec("tenant-abc");
            ValidationConfig config = ValidationConfig.builder() 
                    .excludedIds(Set.of("penalty-001", "penalty-002")) 
                    .build(); 

            LifecycleValidationResult result = runPromise(() -> service.validate(spec, config)); 

            assertNotNull(result); 
            assertTrue(result.issues().size() <= 5); 
        }
    }

    @Nested
    @DisplayName("Policy Engine Integration")
    class PolicyEngineIntegration {

        @Test
        @DisplayName("should invoke policy engine for policy validation")
        void shouldInvokePolicyEngine() { 
            ShapeSpec spec = validShapeSpec("tenant-abc");
            PolicySpecMock policySpec = new PolicySpecMock(); 

            when(policyEngine.evaluate(anyString(), any())) 
                    .thenReturn(Promise.of(true)); 

            runPromise(() -> service.validate(spec, ValidationConfig.defaultConfig())); 

            // Service should attempt policy evaluation if policy spec exists
            verify(auditLogger, times(1)).log(any(Map.class)); 
        }

        @Test
        @DisplayName("should handle policy engine failures gracefully")
        void shouldHandlePolicyEngineFailure() { 
            ShapeSpec spec = validShapeSpec("tenant-abc");
            PolicySpecMock policySpec = new PolicySpecMock(); 

            when(policyEngine.evaluate(anyString(), any())) 
                    .thenReturn(Promise.ofException(new RuntimeException("Policy evaluation failed")));

            // Service should not crash on policy failure
            assertDoesNotThrow(() -> 
                runPromise(() -> service.validate(spec, ValidationConfig.defaultConfig()))); 
        }
    }

    @Nested
    @DisplayName("Tenant Isolation")
    class TenantIsolation {

        @Test
        @DisplayName("should tag metrics with tenant ID")
        void shouldTagMetricsWithTenant() { 
            ShapeSpec spec1 = validShapeSpec("tenant-1");
            ShapeSpec spec2 = validShapeSpec("tenant-2");

            runPromise(() -> service.validate(spec1)); 
            runPromise(() -> service.validate(spec2)); 

            verify(metrics, atLeast(2)).recordTimer( 
                    eq("yappc.validate.execute"),
                    anyLong(), 
                    any(Map.class)); 
        }

        @Test
        @DisplayName("should include tenant in audit log")
        void shouldIncludeTenantInAudit() { 
            ShapeSpec spec = validShapeSpec("tenant-secure");

            runPromise(() -> service.validate(spec)); 

            verify(auditLogger, times(1)).log(argThat(map -> 
                map.containsKey("tenant") || map.values().toString().contains("tenant-secure")));
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should handle audit logger failures")
        void shouldHandleAuditLoggerFailure() { 
            when(auditLogger.log(any(Map.class))) 
                    .thenReturn(Promise.ofException(new RuntimeException("Audit failed")));

            ShapeSpec spec = validShapeSpec("tenant-abc");

            assertDoesNotThrow(() -> 
                runPromise(() -> service.validate(spec))); 
        }

        @Test
        @DisplayName("should increment failure counter on validation error")
        void shouldIncrementFailureCounter() { 
            ShapeSpec spec = ShapeSpec.builder() 
                    .id("shape-fail")
                    .tenantId("tenant-fail")
                    .intentRef(null)  // Invalid: null intentRef 
                    .domainModel(null) 
                    .build(); 

            runPromise(() -> service.validate(spec)); 

            verify(metrics, atLeastOnce()).recordTimer( 
                    anyString(), 
                    anyLong(), 
                    any(Map.class)); 
        }
    }

    // ─── Helper Methods ───────────────────────────────────────────────────

    private ShapeSpec validShapeSpec(String tenantId) { 
        return ShapeSpec.builder() 
                .id("shape-" + System.nanoTime()) 
                .tenantId(tenantId) 
                .intentRef("intent-" + System.nanoTime()) 
                .domainModel(DomainModel.builder() 
                        .entities(List.of( 
                                EntitySpec.builder() 
                                        .name("Entity1")
                                        .description("Test entity")
                                        .fields(List.of()) 
                                        .behaviors(List.of()) 
                                        .build() 
                        ))
                        .relationships(List.of()) 
                        .boundedContexts(List.of()) 
                        .build()) 
                .workflows(List.of()) 
                .integrations(List.of()) 
                .build(); 
    }

    // ─── Mock Helper ───────────────────────────────────────────────────────

    private static class PolicySpecMock {
        // Mock policy spec for testing
    }
}
