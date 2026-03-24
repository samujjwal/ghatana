package com.ghatana.yappc.services.validate;

import com.ghatana.audit.AuditLogger;
import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.shape.DomainModel;
import com.ghatana.yappc.domain.shape.EntitySpec;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.ValidationConfig;
import com.ghatana.yappc.domain.validate.ValidationIssue;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Tests for ValidationService implementation
 * @doc.layer test
 * @doc.pattern Test
 */
class ValidationServiceTest extends EventloopTestBase {
    
    @Test
    void shouldValidateValidShapeSpec() {
        // GIVEN
        PolicyEngine policyEngine = mock(PolicyEngine.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        
        when(auditLogger.log(any(Map.class)))
                .thenReturn(Promise.complete());
        
        ValidationService service = new ValidationServiceImpl(
                policyEngine, auditLogger, metrics);
        
        ShapeSpec spec = ShapeSpec.builder()
                .id("shape-123")
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
        
        // WHEN
        LifecycleValidationResult result = runPromise(() -> service.validate(spec));
        
        // THEN
        assertNotNull(result);
        assertNotNull(result.validatedAt());
        assertNotNull(result.issues());
        
        verify(auditLogger, times(1)).log(any(Map.class));
    }
    
    @Test
    void shouldDetectMissingEntities() {
        // GIVEN
        PolicyEngine policyEngine = mock(PolicyEngine.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        
        when(auditLogger.log(any(Map.class)))
                .thenReturn(Promise.complete());
        
        ValidationService service = new ValidationServiceImpl(
                policyEngine, auditLogger, metrics);
        
        ShapeSpec spec = ShapeSpec.builder()
                .id("shape-123")
                .intentRef("intent-123")
                .domainModel(DomainModel.builder()
                        .entities(List.of())  // Empty entities
                        .relationships(List.of())
                        .boundedContexts(List.of())
                        .build())
                .workflows(List.of())
                .integrations(List.of())
                .build();
        
        // WHEN
        LifecycleValidationResult result = runPromise(() -> service.validate(spec));
        
        // THEN
        assertNotNull(result);
        assertFalse(result.passed());
        assertTrue(result.issues().stream()
                .anyMatch(issue -> issue.id().equals("schema-001")));
    }
    
    @Test
    void shouldValidateWithCustomConfig() {
        // GIVEN
        PolicyEngine policyEngine = mock(PolicyEngine.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        
        when(auditLogger.log(any(Map.class)))
                .thenReturn(Promise.complete());
        
        ValidationService service = new ValidationServiceImpl(
                policyEngine, auditLogger, metrics);
        
        ShapeSpec spec = ShapeSpec.builder()
                .id("shape-123")
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
        
        ValidationConfig config = ValidationConfig.builder()
                .excludedIds(java.util.Set.of("security"))
                .build();
        
        // WHEN
        LifecycleValidationResult result = runPromise(() -> service.validate(spec, config));
        
        // THEN
        assertNotNull(result);
        verify(auditLogger, times(1)).log(any(Map.class));
    }
}
