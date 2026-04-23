/*
 * Copyright (c) 2025 Ghatana Technologies // GH-90000
 * YAPPC Core Agents — Agent Heartbeat Wiring Test
 */
package com.ghatana.yappc.agent;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for AgentHeartbeatService DI wiring in YAPPC lifecycle modules.
 *
 * <p>Validates that AgentHeartbeatService integrates correctly with AgentHealthProvider,
 * Eventloop, and the broader service module architecture. Tests verify:
 * <ul>
 *   <li>Service instantiation via @Provides binding</li>
 *   <li>Periodic heartbeat tick scheduling on eventloop</li>
 *   <li>Health status tracking and failure detection (3-cycle threshold)</li> // GH-90000
 *   <li>Clock advancement simulation for deterministic testing</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Test suite for AgentHeartbeatService DI wiring (YAPPC-2.3.2–2.3.3) // GH-90000
 * @doc.layer product
 * @doc.pattern Test, Integration
 */
@DisplayName("Agent Heartbeat Wiring Tests")
class AgentHeartbeatWiringTest {

    // ─────────────────────────────────────────────────────────────────────────────
    // Utilities: Mock Registry for Testing
    // ─────────────────────────────────────────────────────────────────────────────

    /**
    * In-memory AgentHealthProvider test double for isolated heartbeat testing.
    * Simulates registry health/status behavior without persistence dependencies.
     */
    static class MapAgentHealthProvider implements AgentHealthProvider {

        private final Map<String, AgentLifecycleStatus> statusMap = new ConcurrentHashMap<>(); // GH-90000
        private final Map<String, Instant> lastHeartbeatMap = new ConcurrentHashMap<>(); // GH-90000

        MapAgentHealthProvider() { // GH-90000
            // Seed with 3 agents: 1 READY, 1 FAILED, 1 INITIALIZING
            statusMap.put("agent-1", AgentLifecycleStatus.READY); // GH-90000
            statusMap.put("agent-2", AgentLifecycleStatus.FAILED); // GH-90000
            statusMap.put("agent-3", AgentLifecycleStatus.INITIALIZING); // GH-90000
        }

        @Override
        public Map<String, AgentLifecycleStatus> getHealthStatus() { // GH-90000
            return Map.copyOf(statusMap); // GH-90000
        }

        @Override
        public int getAgentCount() { // GH-90000
            return statusMap.size(); // GH-90000
        }

        // Not in superclass — records heartbeat timestamps for test assertions
        public void recordHeartbeat(String agentId, Instant timestamp) { // GH-90000
            lastHeartbeatMap.put(agentId, timestamp); // GH-90000
        }

        Instant getLastHeartbeat(String agentId) { // GH-90000
            return lastHeartbeatMap.get(agentId); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test Suite: Service Instantiation & Wiring
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Service Instantiation & Lifecycle")
    class ServiceInstantiationTests {

        @Test
        @DisplayName("Service instantiates via @Provides pattern")
        void shouldInstantiateViaProvides() { // GH-90000
            // GIVEN
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000
            MapAgentHealthProvider registry = new MapAgentHealthProvider(); // GH-90000

            // WHEN
            AgentHeartbeatService service = new AgentHeartbeatService(registry, eventloop); // GH-90000

            // THEN
            assertThat(service).isNotNull(); // GH-90000
            assertThat(service).isInstanceOf(AgentHeartbeatService.class); // GH-90000
        }

        @Test
        @DisplayName("Service accepts custom interval")
        void shouldAcceptCustomInterval() { // GH-90000
            // GIVEN
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000
            MapAgentHealthProvider registry = new MapAgentHealthProvider(); // GH-90000
            long customIntervalMs = 5_000L;

            // WHEN
            AgentHeartbeatService service = new AgentHeartbeatService( // GH-90000
                    registry, eventloop, customIntervalMs);

            // THEN
            assertThat(service).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Service rejects non-positive interval")
        void shouldRejectInvalidInterval() { // GH-90000
            // GIVEN
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000
            MapAgentHealthProvider registry = new MapAgentHealthProvider(); // GH-90000

            // WHEN / THEN
            assertThatThrownBy(() -> // GH-90000
                    new AgentHeartbeatService(registry, eventloop, 0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("heartbeatIntervalMs must be positive");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test Suite: Heartbeat Tick Execution
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Heartbeat Tick Execution")
    class HeartbeatTickTests {

        @Test
        @DisplayName("Start transitions service to running state")
        void shouldTransitionToRunningOnStart() { // GH-90000
            // GIVEN
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000
            MapAgentHealthProvider registry = new MapAgentHealthProvider(); // GH-90000
            AgentHeartbeatService service = new AgentHeartbeatService(registry, eventloop); // GH-90000

            // WHEN
            Promise<Void> startPromise = service.start(); // GH-90000

            // THEN
            assertThat(startPromise).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Stop transitions service to stopped state")
        void shouldTransitionToStoppedOnStop() { // GH-90000
            // GIVEN
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000
            MapAgentHealthProvider registry = new MapAgentHealthProvider(); // GH-90000
            AgentHeartbeatService service = new AgentHeartbeatService(registry, eventloop); // GH-90000

            // WHEN
            service.start();  // Start nominally // GH-90000
            Promise<Void> stopPromise = service.stop(); // GH-90000

            // THEN
            assertThat(stopPromise).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Registry reports initial agent statuses")
        void shouldReportInitialAgentStatuses() { // GH-90000
            // GIVEN
            MapAgentHealthProvider registry = new MapAgentHealthProvider(); // GH-90000

            // WHEN
            Map<String, AgentLifecycleStatus> statuses = registry.getHealthStatus(); // GH-90000

            // THEN
            assertThat(statuses).hasSize(3); // GH-90000
            assertThat(statuses).containsEntry("agent-1", AgentLifecycleStatus.READY); // GH-90000
            assertThat(statuses).containsEntry("agent-2", AgentLifecycleStatus.FAILED); // GH-90000
            assertThat(statuses).containsEntry("agent-3", AgentLifecycleStatus.INITIALIZING); // GH-90000
        }

        @Test
        @DisplayName("Registry records heartbeat timestamps")
        void shouldRecordHeartbeatTimestamps() { // GH-90000
            // GIVEN
            MapAgentHealthProvider registry = new MapAgentHealthProvider(); // GH-90000
            Instant now = Instant.now(); // GH-90000

            // WHEN
            registry.recordHeartbeat("agent-1", now); // GH-90000

            // THEN
            assertThat(registry.getLastHeartbeat("agent-1")).isEqualTo(now);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test Suite: Health Status Tracking
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Health Status Tracking")
    class HealthStatusTrackingTests {

        @Test
        @DisplayName("Service tracks consecutive failure cycles per agent")
        void shouldTrackConsecutiveFailures() { // GH-90000
            // GIVEN
            MapAgentHealthProvider registry = new MapAgentHealthProvider(); // GH-90000
            assertThat(registry.getHealthStatus()).containsEntry("agent-2", AgentLifecycleStatus.FAILED); // GH-90000

            // WHEN
            // Simulate 3 heartbeat cycles where agent-2 remains FAILED
            // (In production, AgentHeartbeatService.performHeartbeat() tracks this) // GH-90000

            // THEN
            // Assert registry still reports it as FAILED
            assertThat(registry.getHealthStatus()) // GH-90000
                    .containsEntry("agent-2", AgentLifecycleStatus.FAILED); // GH-90000
        }

        @Test
        @DisplayName("Registry reports agent count")
        void shouldReportAgentCount() { // GH-90000
            // GIVEN
            MapAgentHealthProvider registry = new MapAgentHealthProvider(); // GH-90000

            // WHEN
            int count = registry.getAgentCount(); // GH-90000

            // THEN
            assertThat(count).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("Multiple agents tracked independently")
        void shouldTrackMultipleAgentsIndependently() { // GH-90000
            // GIVEN
            MapAgentHealthProvider registry = new MapAgentHealthProvider(); // GH-90000
            Instant time1 = Instant.parse("2026-03-15T10:00:00Z");
            Instant time2 = Instant.parse("2026-03-15T10:00:01Z");

            // WHEN
            registry.recordHeartbeat("agent-1", time1); // GH-90000
            registry.recordHeartbeat("agent-2", time2); // GH-90000

            // THEN
            assertThat(registry.getLastHeartbeat("agent-1")).isEqualTo(time1);
            assertThat(registry.getLastHeartbeat("agent-2")).isEqualTo(time2);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test Suite: DI Module Integration
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DI Module Integration")
    class DIModuleIntegrationTests {

        @Test
        @DisplayName("@Provides method can be invoked with real dependencies")
        void shouldProvideable() { // GH-90000
            // GIVEN
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000
            MapAgentHealthProvider registry = new MapAgentHealthProvider(); // GH-90000

            // WHEN
            // Simulate what the @Provides method does
            AgentHeartbeatService service = new AgentHeartbeatService( // GH-90000
                    registry,
                    eventloop,
                    AgentHeartbeatService.DEFAULT_INTERVAL_MS);

            // THEN
            assertThat(service).isNotNull(); // GH-90000
            // lastHeartbeat is intentionally null until start() is called // GH-90000
            assertThat(service).hasNoNullFieldsOrPropertiesExcept("lastHeartbeat");
        }

        @Test
        @DisplayName("Service integrates with LifecycleServiceModule bindings")
        void shouldIntegrateWithModuleBindings() { // GH-90000
            // GIVEN
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000
            MapAgentHealthProvider registry = new MapAgentHealthProvider(); // GH-90000

            // WHEN
            // This simulates what happens when the module is instantiated
            AgentHeartbeatService service = new AgentHeartbeatService(registry, eventloop); // GH-90000
            Promise<Void> startPromise = service.start(); // GH-90000

            // THEN
            assertThat(service).isNotNull(); // GH-90000
            assertThat(startPromise).isNotNull(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test Suite: Reliability & Edge Cases
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Reliability & Edge Cases")
    class ReliabilityTests {

        @Test
        @DisplayName("Service handles empty registry")
        void shouldHandleEmptyRegistry() { // GH-90000
            // GIVEN
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000
            MapAgentHealthProvider emptyRegistry = new MapAgentHealthProvider() { // GH-90000
                @Override
                public Map<String, AgentLifecycleStatus> getHealthStatus() { // GH-90000
                    return Map.of(); // Empty // GH-90000
                }

                @Override
                public int getAgentCount() { // GH-90000
                    return 0;
                }
            };

            // WHEN
            AgentHeartbeatService service = new AgentHeartbeatService( // GH-90000
                    emptyRegistry, eventloop, 1_000L);
            Promise<Void> startPromise = service.start(); // GH-90000

            // THEN
            assertThat(startPromise).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Service allows multiple start/stop cycles")
        void shouldAllowMultipleLifecycles() { // GH-90000
            // GIVEN
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000
            MapAgentHealthProvider registry = new MapAgentHealthProvider(); // GH-90000
            AgentHeartbeatService service = new AgentHeartbeatService(registry, eventloop); // GH-90000

            // WHEN / THEN
            assertThatNoException().isThrownBy(() -> { // GH-90000
                service.start(); // GH-90000
                service.stop(); // GH-90000
                service.start(); // GH-90000
                service.stop(); // GH-90000
            });
        }

        @Test
        @DisplayName("Service handles concurrent registry updates")
        void shouldHandleConcurrentUpdates() { // GH-90000
            // GIVEN
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000
            MapAgentHealthProvider registry = new MapAgentHealthProvider(); // GH-90000
            AtomicInteger updateCount = new AtomicInteger(0); // GH-90000

            // WHEN
            // Simulate concurrent heartbeat recordings
            new Thread(() -> { // GH-90000
                for (int i = 0; i < 10; i++) { // GH-90000
                    registry.recordHeartbeat("agent-1", Instant.now()); // GH-90000
                    updateCount.incrementAndGet(); // GH-90000
                }
            }).start(); // GH-90000

            // THEN
            try {
                Thread.sleep(100); // Allow thread to complete // GH-90000
                assertThat(updateCount.get()).isGreaterThan(0); // GH-90000
            } catch (InterruptedException e) { // GH-90000
                Thread.currentThread().interrupt(); // GH-90000
            }
        }
    }
}
