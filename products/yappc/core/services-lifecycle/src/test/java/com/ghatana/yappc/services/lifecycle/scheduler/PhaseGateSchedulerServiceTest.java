/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — Phase Gate Scheduler Test
 */
package com.ghatana.yappc.services.lifecycle.scheduler;

import com.ghatana.yappc.domain.PhaseType;
import com.ghatana.yappc.services.lifecycle.gate.PhaseGateValidator;
import io.activej.eventloop.Eventloop;
import io.activej.inject.annotation.InjectorModule;
import io.activej.inject.module.Module;
import io.activej.inject.module.ModuleBuilder;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for PhaseGateSchedulerService.
 */
@DisplayName("Phase Gate Scheduler Service Tests")
class PhaseGateSchedulerServiceTest {

    private Eventloop eventloop;
    private PhaseGateValidator validator;
    private PhaseGateSchedulerService.ProjectProvider projectProvider;
    private PhaseGateSchedulerService scheduler;

    @BeforeEach
    void setUp() {
        eventloop = Eventloop.create();
        validator = mock(PhaseGateValidator.class);
        projectProvider = mock(PhaseGateSchedulerService.ProjectProvider.class);
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null && scheduler.isRunning()) {
            scheduler.stop();
        }
        eventloop.break();
    }

    @Test
    @DisplayName("Should start and stop scheduler")
    void shouldStartAndStopScheduler() {
        Map<String, String> config = Map.of(
            "yappc.scheduler.phase-gate.interval", "60",
            "yappc.scheduler.phase-gate.enabled", "true"
        );

        scheduler = new PhaseGateSchedulerService(
            eventloop, validator, projectProvider, null, config
        );

        assertFalse(scheduler.isRunning());
        scheduler.start();
        assertTrue(scheduler.isRunning());
        scheduler.stop();
        assertFalse(scheduler.isRunning());
    }

    @Test
    @DisplayName("Should not start when disabled in config")
    void shouldNotStartWhenDisabled() {
        Map<String, String> config = Map.of(
            "yappc.scheduler.phase-gate.interval", "60",
            "yappc.scheduler.phase-gate.enabled", "false"
        );

        scheduler = new PhaseGateSchedulerService(
            eventloop, validator, projectProvider, null, config
        );

        scheduler.start();
        assertFalse(scheduler.isRunning());
    }

    @Test
    @DisplayName("Should check project gates and record results")
    void shouldCheckProjectGates() {
        PhaseGateSchedulerService.ProjectInfo project = new PhaseGateSchedulerService.ProjectInfo(
            "project-1",
            PhaseType.SHAPE,
            Instant.now()
        );

        when(projectProvider.getActiveProjects())
            .thenReturn(Promise.of(List.of(project)));
        
        when(validator.validate(eq("project-1"), eq(PhaseType.SHAPE), any()))
            .thenReturn(Promise.of(new PhaseGateValidator.ValidationResult(
                PhaseType.SHAPE,
                true,
                List.of()
            )));

        Map<String, String> config = Map.of(
            "yappc.scheduler.phase-gate.interval", "60",
            "yappc.scheduler.phase-gate.enabled", "true"
        );

        scheduler = new PhaseGateSchedulerService(
            eventloop, validator, projectProvider, null, config
        );

        scheduler.start();

        // Run eventloop to process scheduled task
        eventloop.run();

        PhaseGateSchedulerService.GateCheckResult result = scheduler.getLastCheckResult("project-1");
        assertNotNull(result);
        assertEquals("project-1", result.projectId());
        assertTrue(result.passed());
    }

    @Test
    @DisplayName("Should record failures for blocked projects")
    void shouldRecordFailuresForBlockedProjects() {
        PhaseGateSchedulerService.ProjectInfo project = new PhaseGateSchedulerService.ProjectInfo(
            "project-1",
            PhaseType.SHAPE,
            Instant.now()
        );

        when(projectProvider.getActiveProjects())
            .thenReturn(Promise.of(List.of(project)));
        
        when(validator.validate(eq("project-1"), eq(PhaseType.SHAPE), any()))
            .thenReturn(Promise.of(new PhaseGateValidator.ValidationResult(
                PhaseType.SHAPE,
                false,
                List.of("missing-artifact: design-doc")
            )));

        Map<String, String> config = Map.of(
            "yappc.scheduler.phase-gate.interval", "60",
            "yappc.scheduler.phase-gate.enabled", "true"
        );

        scheduler = new PhaseGateSchedulerService(
            eventloop, validator, projectProvider, null, config
        );

        scheduler.start();
        eventloop.run();

        List<String> blockedProjects = scheduler.getBlockedProjects();
        assertEquals(1, blockedProjects.size());
        assertEquals("project-1", blockedProjects.get(0));
    }

    @Test
    @DisplayName("Should return correct statistics")
    void shouldReturnCorrectStatistics() {
        Map<String, String> config = Map.of(
            "yappc.scheduler.phase-gate.interval", "60",
            "yappc.scheduler.phase-gate.enabled", "true"
        );

        scheduler = new PhaseGateSchedulerService(
            eventloop, validator, projectProvider, null, config
        );

        PhaseGateSchedulerService.SchedulerStats stats = scheduler.getStats();
        assertEquals(0, stats.totalChecks());
        assertEquals(0, stats.totalFailures());
        assertEquals(Duration.ofSeconds(60), stats.checkInterval());
        assertFalse(stats.running());
    }
}
