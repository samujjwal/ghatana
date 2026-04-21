/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void inMemoryKillSwitchService_activateDeactivate_works() {
        KillSwitchService service = new InMemoryKillSwitchService();
        
        runPromise(() -> service.activate("tenant-1", "test reason", "INC-001"));
        boolean isActive = runPromise(() -> service.isActive("tenant-1"));
        assertThat(isActive).isTrue();
        
        runPromise(() -> service.deactivate("tenant-1", "resolved"));
        isActive = runPromise(() -> service.isActive("tenant-1"));
        assertThat(isActive).isFalse();
    }

    @Test
    @DisplayName("InMemoryKillSwitchService global activate works")
    void inMemoryKillSwitchService_globalActivate_works() {
        KillSwitchService service = new InMemoryKillSwitchService();
        
        runPromise(() -> service.activateGlobal("platform incident", "INC-GLOBAL-001"));
        boolean isGlobalActive = runPromise(() -> service.isGlobalActive());
        assertThat(isGlobalActive).isTrue();
    }

    @Test
    @DisplayName("InMemoryGracefulDegradationManager set and get mode work correctly")
    void inMemoryGracefulDegradationManager_setGetMode_works() {
        GracefulDegradationManager manager = new InMemoryGracefulDegradationManager();
        
        runPromise(() -> manager.setMode("tenant-1", DegradationMode.READ_ONLY));
        DegradationMode mode = runPromise(() -> manager.getMode("tenant-1"));
        assertThat(mode).isEqualTo(DegradationMode.READ_ONLY);
    }

    @Test
    @DisplayName("InMemoryGracefulDegradationManager defaults to NORMAL")
    void inMemoryGracefulDegradationManager_defaultsToNormal() {
        GracefulDegradationManager manager = new InMemoryGracefulDegradationManager();
        
        DegradationMode mode = runPromise(() -> manager.getMode("tenant-new"));
        assertThat(mode).isEqualTo(DegradationMode.NORMAL);
    }

    @Test
    @DisplayName("InMemory implementations handle multiple tenants independently")
    void inMemoryImplementations_multipleTenantsIndependent() {
        KillSwitchService killSwitch = new InMemoryKillSwitchService();
        GracefulDegradationManager degradation = new InMemoryGracefulDegradationManager();
        
        runPromise(() -> killSwitch.activate("tenant-1", "reason", "INC-001"));
        runPromise(() -> killSwitch.activate("tenant-2", "reason", "INC-002"));
        
        boolean tenant1Active = runPromise(() -> killSwitch.isActive("tenant-1"));
        boolean tenant2Active = runPromise(() -> killSwitch.isActive("tenant-2"));
        boolean tenant3Active = runPromise(() -> killSwitch.isActive("tenant-3"));
        
        assertThat(tenant1Active).isTrue();
        assertThat(tenant2Active).isTrue();
        assertThat(tenant3Active).isFalse();
        
        runPromise(() -> degradation.setMode("tenant-1", DegradationMode.READ_ONLY));
        runPromise(() -> degradation.setMode("tenant-2", DegradationMode.DEGRADED));
        
        DegradationMode mode1 = runPromise(() -> degradation.getMode("tenant-1"));
        DegradationMode mode2 = runPromise(() -> degradation.getMode("tenant-2"));
        DegradationMode mode3 = runPromise(() -> degradation.getMode("tenant-3"));
        
        assertThat(mode1).isEqualTo(DegradationMode.READ_ONLY);
        assertThat(mode2).isEqualTo(DegradationMode.DEGRADED);
        assertThat(mode3).isEqualTo(DegradationMode.NORMAL);
    }
}
