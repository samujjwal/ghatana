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
    void shouldCollectObservations() { // GH-90000
        // GIVEN
        AuditLogger auditLogger = mock(AuditLogger.class); // GH-90000
        MetricsCollector metrics = mock(MetricsCollector.class); // GH-90000

        when(auditLogger.log(any(Map.class))) // GH-90000
                .thenReturn(Promise.complete()); // GH-90000

        ObserveService service = new ObserveServiceImpl(metrics, auditLogger); // GH-90000
        RunResult run = RunResult.builder() // GH-90000
                .id("result-123")
                .runSpecRef("run-123")
                .status(com.ghatana.yappc.domain.run.RunStatus.SUCCESS) // GH-90000
                .metadata(java.util.Map.of("environment", "production")) // GH-90000
                .build(); // GH-90000

        // WHEN
        Observation result = runPromise(() -> service.collect(run)); // GH-90000

        // THEN
        assertNotNull(result); // GH-90000
        assertNotNull(result.id()); // GH-90000
        assertEquals("run-123", result.runRef()); // GH-90000
        assertNotNull(result.metrics()); // GH-90000
        assertNotNull(result.logs()); // GH-90000
        assertNotNull(result.traces()); // GH-90000

        verify(auditLogger, times(1)).log(any(Map.class)); // GH-90000
    }

    @Test
    void shouldStreamObservations() { // GH-90000
        // GIVEN
        AuditLogger auditLogger = mock(AuditLogger.class); // GH-90000
        MetricsCollector metrics = mock(MetricsCollector.class); // GH-90000

        when(auditLogger.log(any(Map.class))) // GH-90000
                .thenReturn(Promise.complete()); // GH-90000

        ObserveService service = new ObserveServiceImpl(metrics, auditLogger); // GH-90000
        RunResult run = RunResult.builder() // GH-90000
                .id("result-123")
                .runSpecRef("run-123")
                .status(com.ghatana.yappc.domain.run.RunStatus.RUNNING) // GH-90000
                .metadata(java.util.Map.of("environment", "production")) // GH-90000
                .build(); // GH-90000

        List<Observation> collected = new ArrayList<>(); // GH-90000

        // WHEN
        runPromise(() -> service.streamObservations(run, collected::add)); // GH-90000

        // THEN
        assertFalse(collected.isEmpty()); // GH-90000
        collected.forEach(obs -> { // GH-90000
            assertNotNull(obs); // GH-90000
            assertEquals("run-123", obs.runRef()); // GH-90000
        });
    }

    @Test
    void shouldHandleCollectionFailure() { // GH-90000
        // GIVEN
        AuditLogger auditLogger = mock(AuditLogger.class); // GH-90000
        MetricsCollector metrics = mock(MetricsCollector.class); // GH-90000

        when(auditLogger.log(any(Map.class))) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("Audit failed")));

        ObserveService service = new ObserveServiceImpl(metrics, auditLogger); // GH-90000
        RunResult run = RunResult.builder() // GH-90000
                .id("result-123")
                .runSpecRef("run-123")
                .build(); // GH-90000

        // WHEN/THEN
        try {
            runPromise(() -> service.collect(run)); // GH-90000
            fail("Expected exception");
        } catch (Exception e) { // GH-90000
            assertTrue(e.getMessage().contains("Audit failed"));
        }
    }

    @Test
    void shouldCollectMetricsWithDurationTaskCountAndSuccessRate() { // GH-90000
        // GIVEN
        AuditLogger auditLogger = mock(AuditLogger.class); // GH-90000
        MetricsCollector metrics = mock(MetricsCollector.class); // GH-90000

        when(auditLogger.log(any(Map.class))) // GH-90000
                .thenReturn(Promise.complete()); // GH-90000

        ObserveService service = new ObserveServiceImpl(metrics, auditLogger); // GH-90000
        
        java.time.Instant startedAt = java.time.Instant.now(); // GH-90000
        java.time.Instant completedAt = startedAt.plusSeconds(5); // GH-90000
        
        com.ghatana.yappc.domain.run.TaskResult task1 = com.ghatana.yappc.domain.run.TaskResult.builder() // GH-90000
                .taskId("task-1")
                .status(com.ghatana.yappc.domain.run.RunStatus.SUCCESS) // GH-90000
                .build(); // GH-90000
        com.ghatana.yappc.domain.run.TaskResult task2 = com.ghatana.yappc.domain.run.TaskResult.builder() // GH-90000
                .taskId("task-2")
                .status(com.ghatana.yappc.domain.run.RunStatus.SUCCESS) // GH-90000
                .build(); // GH-90000
        com.ghatana.yappc.domain.run.TaskResult task3 = com.ghatana.yappc.domain.run.TaskResult.builder() // GH-90000
                .taskId("task-3")
                .status(com.ghatana.yappc.domain.run.RunStatus.FAILED) // GH-90000
                .build(); // GH-90000

        RunResult run = RunResult.builder() // GH-90000
                .id("result-123")
                .runSpecRef("run-123")
                .status(com.ghatana.yappc.domain.run.RunStatus.FAILED) // GH-90000
                .startedAt(startedAt) // GH-90000
                .completedAt(completedAt) // GH-90000
                .taskResults(List.of(task1, task2, task3)) // GH-90000
                .metadata(java.util.Map.of("environment", "production")) // GH-90000
                .build(); // GH-90000

        // WHEN
        Observation result = runPromise(() -> service.collect(run)); // GH-90000

        // THEN
        assertNotNull(result); // GH-90000
        assertNotNull(result.metrics()); // GH-90000
        
        // Verify metrics include run.duration, run.task_count, and run.success_rate
        boolean hasDuration = result.metrics().stream() // GH-90000
                .anyMatch(m -> m.name().equals("run.duration"));
        boolean hasTaskCount = result.metrics().stream() // GH-90000
                .anyMatch(m -> m.name().equals("run.task_count"));
        boolean hasSuccessRate = result.metrics().stream() // GH-90000
                .anyMatch(m -> m.name().equals("run.success_rate"));
        
        assertTrue(hasDuration, "Metrics should include run.duration"); // GH-90000
        assertTrue(hasTaskCount, "Metrics should include run.task_count"); // GH-90000
        assertTrue(hasSuccessRate, "Metrics should include run.success_rate"); // GH-90000
        
        // Verify metric values
        result.metrics().stream() // GH-90000
                .filter(m -> m.name().equals("run.duration"))
                .forEach(m -> { // GH-90000
                                        assertTrue(m.value() >= 0.0d); // GH-90000
                });
        
        result.metrics().stream() // GH-90000
                .filter(m -> m.name().equals("run.task_count"))
                .forEach(m -> { // GH-90000
                                        assertEquals(3.0d, m.value(), 0.001d); // GH-90000
                });
        
        result.metrics().stream() // GH-90000
                .filter(m -> m.name().equals("run.success_rate"))
                .forEach(m -> { // GH-90000
                                        assertEquals(66.66666666666666d, m.value(), 0.001d); // GH-90000
                });
    }
}
