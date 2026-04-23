/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.incident;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link InMemoryKillSwitchService} and {@link InMemoryGracefulDegradationManager}.
 *
 * @doc.type class
 * @doc.purpose Integration tests for in-memory incident management implementations
 * @doc.layer shared-service
 * @doc.pattern Test
 */
@Tag("integration")
@DisplayName("InMemory implementations — integration tests")
class IncidentServiceLauncherIntegrationTest extends EventloopTestBase {

    @Test
    @DisplayName("InMemoryKillSwitchService activate and deactivate work correctly")
    void inMemoryKillSwitchService_activateDeactivate_works() { // GH-90000
        KillSwitchService service = new InMemoryKillSwitchService(); // GH-90000
        
        runPromise(() -> service.activate("tenant-1", "test reason", "INC-001")); // GH-90000
        boolean isActive = runPromise(() -> service.isActive("tenant-1"));
        assertThat(isActive).isTrue(); // GH-90000
        
        runPromise(() -> service.deactivate("tenant-1", "resolved")); // GH-90000
        isActive = runPromise(() -> service.isActive("tenant-1"));
        assertThat(isActive).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("InMemoryKillSwitchService global activate works")
    void inMemoryKillSwitchService_globalActivate_works() { // GH-90000
        KillSwitchService service = new InMemoryKillSwitchService(); // GH-90000
        
        runPromise(() -> service.activateGlobal("platform incident", "INC-GLOBAL-001")); // GH-90000
        boolean isGlobalActive = runPromise(() -> service.isGlobalActive()); // GH-90000
        assertThat(isGlobalActive).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("InMemoryGracefulDegradationManager set and get mode work correctly")
    void inMemoryGracefulDegradationManager_setGetMode_works() { // GH-90000
        GracefulDegradationManager manager = new InMemoryGracefulDegradationManager(); // GH-90000
        
        runPromise(() -> manager.setMode("tenant-1", DegradationMode.READ_ONLY)); // GH-90000
        DegradationMode mode = runPromise(() -> manager.getMode("tenant-1"));
        assertThat(mode).isEqualTo(DegradationMode.READ_ONLY); // GH-90000
    }

    @Test
    @DisplayName("InMemoryGracefulDegradationManager defaults to FULL")
    void inMemoryGracefulDegradationManager_defaultsToFull() { // GH-90000
        GracefulDegradationManager manager = new InMemoryGracefulDegradationManager(); // GH-90000
        
        DegradationMode mode = runPromise(() -> manager.getMode("tenant-new"));
        assertThat(mode).isEqualTo(DegradationMode.FULL); // GH-90000
    }

    @Test
    @DisplayName("InMemory implementations handle multiple tenants independently")
    void inMemoryImplementations_multipleTenantsIndependent() { // GH-90000
        KillSwitchService killSwitch = new InMemoryKillSwitchService(); // GH-90000
        GracefulDegradationManager degradation = new InMemoryGracefulDegradationManager(); // GH-90000
        
        runPromise(() -> killSwitch.activate("tenant-1", "reason", "INC-001")); // GH-90000
        runPromise(() -> killSwitch.activate("tenant-2", "reason", "INC-002")); // GH-90000
        
        boolean tenant1Active = runPromise(() -> killSwitch.isActive("tenant-1"));
        boolean tenant2Active = runPromise(() -> killSwitch.isActive("tenant-2"));
        boolean tenant3Active = runPromise(() -> killSwitch.isActive("tenant-3"));
        
        assertThat(tenant1Active).isTrue(); // GH-90000
        assertThat(tenant2Active).isTrue(); // GH-90000
        assertThat(tenant3Active).isFalse(); // GH-90000
        
        runPromise(() -> degradation.setMode("tenant-1", DegradationMode.READ_ONLY)); // GH-90000
        runPromise(() -> degradation.setMode("tenant-2", DegradationMode.NOTIFICATIONS_ONLY)); // GH-90000
        
        DegradationMode mode1 = runPromise(() -> degradation.getMode("tenant-1"));
        DegradationMode mode2 = runPromise(() -> degradation.getMode("tenant-2"));
        DegradationMode mode3 = runPromise(() -> degradation.getMode("tenant-3"));
        
        assertThat(mode1).isEqualTo(DegradationMode.READ_ONLY); // GH-90000
        assertThat(mode2).isEqualTo(DegradationMode.NOTIFICATIONS_ONLY); // GH-90000
        assertThat(mode3).isEqualTo(DegradationMode.FULL); // GH-90000
    }
}
