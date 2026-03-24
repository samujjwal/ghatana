package com.ghatana.yappc.services.run;

import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.run.RunSpec;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentMatchers;

/**
 * @doc.type class
 * @doc.purpose Tests for RunService implementation
 * @doc.layer test
 * @doc.pattern Test
 */
class RunServiceTest extends EventloopTestBase {
    
    @Test
    void shouldExecuteRunSpec() {
        // GIVEN
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        
        when(auditLogger.log(any(Map.class)))
                .thenReturn(Promise.complete());
        
        RunService service = new RunServiceImpl(auditLogger, metrics);
        RunSpec spec = RunSpec.builder()
                .id("run-123")
                .artifactsRef("artifacts-123")
                .environment("staging")
                .tasks(List.of())
                .config(java.util.Map.of("tenantId", "tenant-123"))
                .build();
        
        // WHEN
        RunResult result = runPromise(() -> service.execute(spec));
        
        // THEN
        assertNotNull(result);
        assertNotNull(result.id());
        assertEquals("run-123", result.runSpecRef());
        assertEquals("staging", result.metadata().getOrDefault("environment", ""));
        assertNotNull(result.status());
        
        verify(auditLogger, times(1)).log(any(Map.class));
        verify(metrics, atLeastOnce()).recordTimer(anyString(), anyLong(), any(Map.class));
    }
    
    @Test
    void shouldRollbackDeployment() {
        // GIVEN
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        
        when(auditLogger.log(any(Map.class)))
                .thenReturn(Promise.complete());
        
        RunService service = new RunServiceImpl(auditLogger, metrics);
        
        // WHEN
        RunResult result = runPromise(() -> service.rollback("deploy-123", "v1.0.0"));
        
        // THEN
        assertNotNull(result);
        assertNotNull(result.status());
        
        verify(auditLogger, times(1)).log(any(Map.class));
    }
    
    @Test
    void shouldPromoteDeployment() {
        // GIVEN
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        
        when(auditLogger.log(any(Map.class)))
                .thenReturn(Promise.complete());
        
        RunService service = new RunServiceImpl(auditLogger, metrics);
        
        // WHEN
        RunResult result = runPromise(() -> service.promote("deploy-123", "production"));
        
        // THEN
        assertNotNull(result);
        assertNotNull(result.status());
        assertEquals("production", result.metadata().getOrDefault("environment", ""));
        
        verify(auditLogger, times(1)).log(any(Map.class));
    }
}
