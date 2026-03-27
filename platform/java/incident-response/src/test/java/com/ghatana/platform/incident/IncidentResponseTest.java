/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.incident;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InMemoryKillSwitchService} and {@link InMemoryGracefulDegradationManager}.
 */
@DisplayName("Incident Response")
class IncidentResponseTest extends EventloopTestBase {

    @Nested
    @DisplayName("InMemoryKillSwitchService")
    class KillSwitchTests {

        private InMemoryKillSwitchService killSwitch;

        @BeforeEach
        void setUp() {
            killSwitch = new InMemoryKillSwitchService();
        }

        @Test
        @DisplayName("initially not active for any tenant")
        void initiallyInactive() {
            boolean active = runPromise(() -> killSwitch.isActive("t1"));
            assertThat(active).isFalse();
        }

        @Test
        @DisplayName("activate makes isActive return true for that tenant")
        void activatesTenant() {
            runBlocking(() -> killSwitch.activate("t1", "security incident", "inc-1"));
            assertThat(runPromise(() -> killSwitch.isActive("t1"))).isTrue();
        }

        @Test
        @DisplayName("deactivate makes isActive return false")
        void deactivatesTenant() {
            runBlocking(() -> killSwitch.activate("t1", "incident", "inc-1"));
            runBlocking(() -> killSwitch.deactivate("t1", "resolved"));
            assertThat(runPromise(() -> killSwitch.isActive("t1"))).isFalse();
        }

        @Test
        @DisplayName("activating one tenant does not affect another")
        void tenantIsolation() {
            runBlocking(() -> killSwitch.activate("t1", "reason", "inc-1"));
            assertThat(runPromise(() -> killSwitch.isActive("t2"))).isFalse();
        }

        @Test
        @DisplayName("global activation makes all tenants report as active")
        void globalActivation() {
            runBlocking(() -> killSwitch.activateGlobal("critical incident", "inc-global"));
            assertThat(runPromise(() -> killSwitch.isGlobalActive())).isTrue();
            assertThat(runPromise(() -> killSwitch.isActive("any-tenant"))).isTrue();
        }

        @Test
        @DisplayName("reset clears all state")
        void resetClearsState() {
            runBlocking(() -> killSwitch.activate("t1", "r", "i"));
            runBlocking(() -> killSwitch.activateGlobal("r", "i"));
            killSwitch.reset();
            assertThat(runPromise(() -> killSwitch.isActive("t1"))).isFalse();
            assertThat(runPromise(() -> killSwitch.isGlobalActive())).isFalse();
        }
    }

    @Nested
    @DisplayName("InMemoryGracefulDegradationManager")
    class DegradationTests {

        private InMemoryGracefulDegradationManager manager;

        @BeforeEach
        void setUp() {
            manager = new InMemoryGracefulDegradationManager();
        }

        @Test
        @DisplayName("default mode is FULL")
        void defaultModeFull() {
            DegradationMode mode = runPromise(() -> manager.getMode("t1"));
            assertThat(mode).isEqualTo(DegradationMode.FULL);
        }

        @Test
        @DisplayName("FULL mode allows all actions")
        void fullModeAllowsAll() {
            assertThat(runPromise(() -> manager.isActionAllowed("t1", "WRITE"))).isTrue();
            assertThat(runPromise(() -> manager.isActionAllowed("t1", "DELETE"))).isTrue();
        }

        @Test
        @DisplayName("READ_ONLY mode allows reads but blocks writes")
        void readOnlyBlocksWrites() {
            runBlocking(() -> manager.setMode("t1", DegradationMode.READ_ONLY));
            assertThat(runPromise(() -> manager.isActionAllowed("t1", "READ"))).isTrue();
            assertThat(runPromise(() -> manager.isActionAllowed("t1", "WRITE"))).isFalse();
        }

        @Test
        @DisplayName("NOTIFICATIONS_ONLY allows NOTIFY only")
        void notificationsOnlyAllowsNotify() {
            runBlocking(() -> manager.setMode("t1", DegradationMode.NOTIFICATIONS_ONLY));
            assertThat(runPromise(() -> manager.isActionAllowed("t1", "NOTIFY"))).isTrue();
            assertThat(runPromise(() -> manager.isActionAllowed("t1", "READ"))).isFalse();
        }

        @Test
        @DisplayName("OFFLINE blocks all actions")
        void offlineBlocksAll() {
            runBlocking(() -> manager.setMode("t1", DegradationMode.OFFLINE));
            assertThat(runPromise(() -> manager.isActionAllowed("t1", "READ"))).isFalse();
            assertThat(runPromise(() -> manager.isActionAllowed("t1", "NOTIFY"))).isFalse();
        }

        @Test
        @DisplayName("setting mode back to FULL restores all actions")
        void restoreToFull() {
            runBlocking(() -> manager.setMode("t1", DegradationMode.OFFLINE));
            runBlocking(() -> manager.setMode("t1", DegradationMode.FULL));
            assertThat(runPromise(() -> manager.isActionAllowed("t1", "WRITE"))).isTrue();
        }

        @Test
        @DisplayName("tenants are isolated")
        void tenantsAreIsolated() {
            runBlocking(() -> manager.setMode("t1", DegradationMode.OFFLINE));
            assertThat(runPromise(() -> manager.isActionAllowed("t2", "WRITE"))).isTrue();
        }
    }
}
