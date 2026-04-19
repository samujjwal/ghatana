package com.ghatana.yappc.services.observe;

import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.observe.Observation;
import com.ghatana.yappc.domain.run.RunResult;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Tests for ObserveService implementation
 * @doc.layer test
 * @doc.pattern Test
 */
class ObserveServiceTest extends EventloopTestBase {

    @Test
    void shouldCollectObservations() {
        // GIVEN
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);

        when(auditLogger.log(any(Map.class)))
                .thenReturn(Promise.complete());

        ObserveService service = new ObserveServiceImpl(metrics, auditLogger);
        RunResult run = RunResult.builder()
                .id("result-123")
                .runSpecRef("run-123")
                .status(com.ghatana.yappc.domain.run.RunStatus.SUCCESS)
                .metadata(java.util.Map.of("environment", "production"))
                .build();

        // WHEN
        Observation result = runPromise(() -> service.collect(run));

        // THEN
        assertNotNull(result);
        assertNotNull(result.id());
        assertEquals("run-123", result.runRef());
        assertNotNull(result.metrics());
        assertNotNull(result.logs());
        assertNotNull(result.traces());

        verify(auditLogger, times(1)).log(any(Map.class));
    }

    @Test
    void shouldStreamObservations() {
        // GIVEN
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);

        when(auditLogger.log(any(Map.class)))
                .thenReturn(Promise.complete());

        ObserveService service = new ObserveServiceImpl(metrics, auditLogger);
        RunResult run = RunResult.builder()
                .id("result-123")
                .runSpecRef("run-123")
                .status(com.ghatana.yappc.domain.run.RunStatus.RUNNING)
                .metadata(java.util.Map.of("environment", "production"))
                .build();

        List<Observation> collected = new ArrayList<>();

        // WHEN
        runPromise(() -> service.streamObservations(run, collected::add));

        // THEN
        assertFalse(collected.isEmpty());
        collected.forEach(obs -> {
            assertNotNull(obs);
            assertEquals("run-123", obs.runRef());
        });
    }

    @Test
    void shouldHandleCollectionFailure() {
        // GIVEN
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);

        when(auditLogger.log(any(Map.class)))
                .thenReturn(Promise.ofException(new RuntimeException("Audit failed")));

        ObserveService service = new ObserveServiceImpl(metrics, auditLogger);
        RunResult run = RunResult.builder()
                .id("result-123")
                .runSpecRef("run-123")
                .build();

        // WHEN/THEN
        try {
            runPromise(() -> service.collect(run));
            fail("Expected exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Audit failed"));
        }
    }

    @Test
    void shouldCollectMetricsWithDurationTaskCountAndSuccessRate() {
        // GIVEN
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);

        when(auditLogger.log(any(Map.class)))
                .thenReturn(Promise.complete());

        ObserveService service = new ObserveServiceImpl(metrics, auditLogger);
        
        java.time.Instant startedAt = java.time.Instant.now();
        java.time.Instant completedAt = startedAt.plusSeconds(5);
        
        com.ghatana.yappc.domain.run.TaskResult task1 = com.ghatana.yappc.domain.run.TaskResult.builder()
                .taskId("task-1")
                .status(com.ghatana.yappc.domain.run.RunStatus.SUCCESS)
                .build();
        com.ghatana.yappc.domain.run.TaskResult task2 = com.ghatana.yappc.domain.run.TaskResult.builder()
                .taskId("task-2")
                .status(com.ghatana.yappc.domain.run.RunStatus.SUCCESS)
                .build();
        com.ghatana.yappc.domain.run.TaskResult task3 = com.ghatana.yappc.domain.run.TaskResult.builder()
                .taskId("task-3")
                .status(com.ghatana.yappc.domain.run.RunStatus.FAILED)
                .build();

        RunResult run = RunResult.builder()
                .id("result-123")
                .runSpecRef("run-123")
                .status(com.ghatana.yappc.domain.run.RunStatus.FAILED)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .taskResults(List.of(task1, task2, task3))
                .metadata(java.util.Map.of("environment", "production"))
                .build();

        // WHEN
        Observation result = runPromise(() -> service.collect(run));

        // THEN
        assertNotNull(result);
        assertNotNull(result.metrics());
        
        // Verify metrics include run.duration, run.task_count, and run.success_rate
        boolean hasDuration = result.metrics().stream()
                .anyMatch(m -> m.name().equals("run.duration"));
        boolean hasTaskCount = result.metrics().stream()
                .anyMatch(m -> m.name().equals("run.task_count"));
        boolean hasSuccessRate = result.metrics().stream()
                .anyMatch(m -> m.name().equals("run.success_rate"));
        
        assertTrue(hasDuration, "Metrics should include run.duration");
        assertTrue(hasTaskCount, "Metrics should include run.task_count");
        assertTrue(hasSuccessRate, "Metrics should include run.success_rate");
        
        // Verify metric values
        result.metrics().stream()
                .filter(m -> m.name().equals("run.duration"))
                .forEach(m -> {
                                        assertTrue(m.value() >= 0.0d);
                });
        
        result.metrics().stream()
                .filter(m -> m.name().equals("run.task_count"))
                .forEach(m -> {
                                        assertEquals(3.0d, m.value(), 0.001d);
                });
        
        result.metrics().stream()
                .filter(m -> m.name().equals("run.success_rate"))
                .forEach(m -> {
                                        assertEquals(66.66666666666666d, m.value(), 0.001d);
                });
    }
}
