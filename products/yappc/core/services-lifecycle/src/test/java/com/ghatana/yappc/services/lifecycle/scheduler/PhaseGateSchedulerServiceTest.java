/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
 * YAPPC Lifecycle Service — Phase Gate Scheduler Test
 */
package com.ghatana.yappc.services.lifecycle.scheduler;

import com.ghatana.core.activej.testing.EventloopTestExtension;
import com.ghatana.core.activej.testing.EventloopTestRunner;
import com.ghatana.yappc.services.lifecycle.gate.PhaseGateValidator;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for PhaseGateSchedulerService using EventloopTestExtension for proper
 * async test lifecycle management with timeouts.
 */
@DisplayName("Phase Gate Scheduler Service Tests [GH-90000]")
@ExtendWith(EventloopTestExtension.class) // GH-90000
class PhaseGateSchedulerServiceTest {

    private PhaseGateValidator validator;
    private PhaseGateSchedulerService.ProjectProvider projectProvider;
    private PhaseGateSchedulerService scheduler;

    void setupMocks() { // GH-90000
        validator = mock(PhaseGateValidator.class); // GH-90000
        projectProvider = mock(PhaseGateSchedulerService.ProjectProvider.class); // GH-90000
    }

    @Test
    @DisplayName("Should start and stop scheduler [GH-90000]")
    void shouldStartAndStopScheduler(EventloopTestRunner runner) { // GH-90000
        setupMocks(); // GH-90000
        // Use long interval (1 hour) so scheduled check doesn't run during this test // GH-90000
        // This test only verifies start/stop state machine, not the actual checking
        Map<String, String> config = Map.of( // GH-90000
            "yappc.scheduler.phase-gate.interval", "3600",
            "yappc.scheduler.phase-gate.enabled", "true"
        );

        runner.runPromise(() -> { // GH-90000
            scheduler = new PhaseGateSchedulerService( // GH-90000
                runner.eventloop(), validator, projectProvider, null, config // GH-90000
            );

            assertFalse(scheduler.isRunning()); // GH-90000
            scheduler.start(); // GH-90000
            assertTrue(scheduler.isRunning()); // GH-90000
            scheduler.stop(); // GH-90000
            assertFalse(scheduler.isRunning()); // GH-90000
            return Promise.of(null); // GH-90000
        });
    }

    @Test
    @DisplayName("Should not start when disabled in config [GH-90000]")
    void shouldNotStartWhenDisabled(EventloopTestRunner runner) { // GH-90000
        setupMocks(); // GH-90000
        Map<String, String> config = Map.of( // GH-90000
            "yappc.scheduler.phase-gate.interval", "0",
            "yappc.scheduler.phase-gate.enabled", "false"
        );

        runner.runPromise(() -> { // GH-90000
            scheduler = new PhaseGateSchedulerService( // GH-90000
                runner.eventloop(), validator, projectProvider, null, config // GH-90000
            );

            scheduler.start(); // GH-90000
            assertFalse(scheduler.isRunning()); // GH-90000
            return Promise.of(null); // GH-90000
        });
    }

    @Test
    @DisplayName("Should parse config and set check interval correctly [GH-90000]")
    void shouldParseConfigAndSetCheckInterval(EventloopTestRunner runner) { // GH-90000
        setupMocks(); // GH-90000
        // Test various interval configurations
        Map<String, String> config = Map.of( // GH-90000
            "yappc.scheduler.phase-gate.interval", "300",
            "yappc.scheduler.phase-gate.enabled", "true"
        );

        runner.runPromise(() -> { // GH-90000
            scheduler = new PhaseGateSchedulerService( // GH-90000
                runner.eventloop(), validator, projectProvider, null, config // GH-90000
            );

            PhaseGateSchedulerService.SchedulerStats stats = scheduler.getStats(); // GH-90000
            assertEquals(Duration.ofSeconds(300), stats.checkInterval()); // GH-90000
            assertTrue(scheduler.isRunning() == false); // Not started yet // GH-90000
            return Promise.of(null); // GH-90000
        });
    }

    @Test
    @DisplayName("Should track blocked projects correctly [GH-90000]")
    void shouldTrackBlockedProjects(EventloopTestRunner runner) { // GH-90000
        setupMocks(); // GH-90000
        // Verify blocked projects list is initially empty
        Map<String, String> config = Map.of( // GH-90000
            "yappc.scheduler.phase-gate.interval", "3600",
            "yappc.scheduler.phase-gate.enabled", "true"
        );

        runner.runPromise(() -> { // GH-90000
            scheduler = new PhaseGateSchedulerService( // GH-90000
                runner.eventloop(), validator, projectProvider, null, config // GH-90000
            );

            List<String> blockedProjects = scheduler.getBlockedProjects(); // GH-90000
            assertTrue(blockedProjects.isEmpty(), "Blocked projects should be empty initially"); // GH-90000
            return Promise.of(null); // GH-90000
        });
    }

    @Test
    @DisplayName("Should return correct statistics [GH-90000]")
    void shouldReturnCorrectStatistics(EventloopTestRunner runner) { // GH-90000
        setupMocks(); // GH-90000
        Map<String, String> config = Map.of( // GH-90000
            "yappc.scheduler.phase-gate.interval", "60",
            "yappc.scheduler.phase-gate.enabled", "true"
        );

        runner.runPromise(() -> { // GH-90000
            scheduler = new PhaseGateSchedulerService( // GH-90000
                runner.eventloop(), validator, projectProvider, null, config // GH-90000
            );

            PhaseGateSchedulerService.SchedulerStats stats = scheduler.getStats(); // GH-90000
            assertEquals(0, stats.totalChecks()); // GH-90000
            assertEquals(0, stats.totalFailures()); // GH-90000
            assertEquals(Duration.ofSeconds(60), stats.checkInterval()); // GH-90000
            assertFalse(stats.running()); // GH-90000
            return Promise.of(null); // GH-90000
        });
    }
}
