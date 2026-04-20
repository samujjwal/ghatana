/*
 * Copyright (c) 2026 Ghatana Technologies
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
@DisplayName("Phase Gate Scheduler Service Tests")
@ExtendWith(EventloopTestExtension.class)
class PhaseGateSchedulerServiceTest {

    private PhaseGateValidator validator;
    private PhaseGateSchedulerService.ProjectProvider projectProvider;
    private PhaseGateSchedulerService scheduler;

    void setupMocks() {
        validator = mock(PhaseGateValidator.class);
        projectProvider = mock(PhaseGateSchedulerService.ProjectProvider.class);
    }

    @Test
    @DisplayName("Should start and stop scheduler")
    void shouldStartAndStopScheduler(EventloopTestRunner runner) {
        setupMocks();
        // Use long interval (1 hour) so scheduled check doesn't run during this test
        // This test only verifies start/stop state machine, not the actual checking
        Map<String, String> config = Map.of(
            "yappc.scheduler.phase-gate.interval", "3600",
            "yappc.scheduler.phase-gate.enabled", "true"
        );

        runner.runPromise(() -> {
            scheduler = new PhaseGateSchedulerService(
                runner.eventloop(), validator, projectProvider, null, config
            );

            assertFalse(scheduler.isRunning());
            scheduler.start();
            assertTrue(scheduler.isRunning());
            scheduler.stop();
            assertFalse(scheduler.isRunning());
            return Promise.of(null);
        });
    }

    @Test
    @DisplayName("Should not start when disabled in config")
    void shouldNotStartWhenDisabled(EventloopTestRunner runner) {
        setupMocks();
        Map<String, String> config = Map.of(
            "yappc.scheduler.phase-gate.interval", "0",
            "yappc.scheduler.phase-gate.enabled", "false"
        );

        runner.runPromise(() -> {
            scheduler = new PhaseGateSchedulerService(
                runner.eventloop(), validator, projectProvider, null, config
            );

            scheduler.start();
            assertFalse(scheduler.isRunning());
            return Promise.of(null);
        });
    }

    @Test
    @DisplayName("Should parse config and set check interval correctly")
    void shouldParseConfigAndSetCheckInterval(EventloopTestRunner runner) {
        setupMocks();
        // Test various interval configurations
        Map<String, String> config = Map.of(
            "yappc.scheduler.phase-gate.interval", "300",
            "yappc.scheduler.phase-gate.enabled", "true"
        );

        runner.runPromise(() -> {
            scheduler = new PhaseGateSchedulerService(
                runner.eventloop(), validator, projectProvider, null, config
            );

            PhaseGateSchedulerService.SchedulerStats stats = scheduler.getStats();
            assertEquals(Duration.ofSeconds(300), stats.checkInterval());
            assertTrue(scheduler.isRunning() == false); // Not started yet
            return Promise.of(null);
        });
    }

    @Test
    @DisplayName("Should track blocked projects correctly")
    void shouldTrackBlockedProjects(EventloopTestRunner runner) {
        setupMocks();
        // Verify blocked projects list is initially empty
        Map<String, String> config = Map.of(
            "yappc.scheduler.phase-gate.interval", "3600",
            "yappc.scheduler.phase-gate.enabled", "true"
        );

        runner.runPromise(() -> {
            scheduler = new PhaseGateSchedulerService(
                runner.eventloop(), validator, projectProvider, null, config
            );

            List<String> blockedProjects = scheduler.getBlockedProjects();
            assertTrue(blockedProjects.isEmpty(), "Blocked projects should be empty initially");
            return Promise.of(null);
        });
    }

    @Test
    @DisplayName("Should return correct statistics")
    void shouldReturnCorrectStatistics(EventloopTestRunner runner) {
        setupMocks();
        Map<String, String> config = Map.of(
            "yappc.scheduler.phase-gate.interval", "60",
            "yappc.scheduler.phase-gate.enabled", "true"
        );

        runner.runPromise(() -> {
            scheduler = new PhaseGateSchedulerService(
                runner.eventloop(), validator, projectProvider, null, config
            );

            PhaseGateSchedulerService.SchedulerStats stats = scheduler.getStats();
            assertEquals(0, stats.totalChecks());
            assertEquals(0, stats.totalFailures());
            assertEquals(Duration.ofSeconds(60), stats.checkInterval());
            assertFalse(stats.running());
            return Promise.of(null);
        });
    }
}
