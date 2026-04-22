/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.storage.connector;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for data connector lifecycle — registration, activation,
 * error handling, and fallback behaviour.
 *
 * @doc.type    class
 * @doc.purpose Tests for storage connectors: lifecycle, error handling, fallback
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("Storage Connector Tests [GH-90000]")
class ConnectorTest extends EventloopTestBase {

    // ── Connector model ───────────────────────────────────────────────────────

    enum ConnectorStatus { REGISTERED, ACTIVE, FAILED, DEACTIVATED }

    interface DataConnector {
        String connectorId(); // GH-90000
        boolean isHealthy(); // GH-90000
        List<String> readBatch(int batchSize); // GH-90000
    }

    record ConnectorState(DataConnector connector, ConnectorStatus status, String lastError) {} // GH-90000

    private ConnectorRegistry registry;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new ConnectorRegistry(); // GH-90000
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("registered connector is available in REGISTERED status [GH-90000]")
    void registeredConnectorAvailableInRegisteredStatus() { // GH-90000
        DataConnector conn = makeHealthyConnector("conn-1", List.of("record-1 [GH-90000]"));
        registry.register(conn); // GH-90000

        assertThat(registry.status("conn-1 [GH-90000]")).isEqualTo(ConnectorStatus.REGISTERED);
    }

    @Test
    @DisplayName("registering same connector ID twice is rejected [GH-90000]")
    void registeringDuplicateConnectorIdIsRejected() { // GH-90000
        DataConnector conn = makeHealthyConnector("conn-dup", List.of()); // GH-90000
        registry.register(conn); // GH-90000

        assertThatThrownBy(() -> registry.register(conn)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("already registered [GH-90000]");
    }

    // ── Activation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("healthy connector transitions to ACTIVE on activation [GH-90000]")
    void healthyConnectorTransitionsToActiveOnActivation() { // GH-90000
        DataConnector conn = makeHealthyConnector("conn-act", List.of("r1", "r2")); // GH-90000
        registry.register(conn); // GH-90000
        registry.activate("conn-act [GH-90000]");

        assertThat(registry.status("conn-act [GH-90000]")).isEqualTo(ConnectorStatus.ACTIVE);
    }

    @Test
    @DisplayName("activating an unhealthy connector sets status to FAILED [GH-90000]")
    void activatingUnhealthyConnectorSetsStatusToFailed() { // GH-90000
        DataConnector unhealthy = makeUnhealthyConnector("conn-sick [GH-90000]");
        registry.register(unhealthy); // GH-90000
        registry.activate("conn-sick [GH-90000]");

        assertThat(registry.status("conn-sick [GH-90000]")).isEqualTo(ConnectorStatus.FAILED);
    }

    @Test
    @DisplayName("active connector can read batches of data [GH-90000]")
    void activeConnectorCanReadBatches() { // GH-90000
        DataConnector conn = makeHealthyConnector("conn-read", List.of("a", "b", "c")); // GH-90000
        registry.register(conn); // GH-90000
        registry.activate("conn-read [GH-90000]");

        List<String> batch = registry.readBatch("conn-read", 2); // GH-90000
        assertThat(batch).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("inactive connector cannot read data [GH-90000]")
    void inactiveConnectorCannotReadData() { // GH-90000
        DataConnector conn = makeHealthyConnector("conn-inactive", List.of("x [GH-90000]"));
        registry.register(conn); // GH-90000

        assertThatThrownBy(() -> registry.readBatch("conn-inactive", 10)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("not active [GH-90000]");
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    @DisplayName("error during read is captured and connector is marked FAILED [GH-90000]")
    void errorDuringReadCapturedAndConnectorMarkedFailed() { // GH-90000
        AtomicInteger callCount = new AtomicInteger(0); // GH-90000
        DataConnector flakyConn = new DataConnector() { // GH-90000
            @Override public String connectorId() { return "conn-flaky"; } // GH-90000
            @Override public boolean isHealthy() { return true; } // GH-90000
            @Override public List<String> readBatch(int batchSize) { // GH-90000
                callCount.incrementAndGet(); // GH-90000
                throw new RuntimeException("Simulated IO error [GH-90000]");
            }
        };

        registry.register(flakyConn); // GH-90000
        registry.activate("conn-flaky [GH-90000]");

        try {
            registry.readBatch("conn-flaky", 10); // GH-90000
        } catch (RuntimeException ignored) {} // GH-90000

        assertThat(registry.status("conn-flaky [GH-90000]")).isEqualTo(ConnectorStatus.FAILED);
        assertThat(registry.lastError("conn-flaky [GH-90000]")).contains("Simulated IO error [GH-90000]");
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("primary connector failure triggers fallback connector read [GH-90000]")
    void primaryFailureTriggersFallback() { // GH-90000
        DataConnector primary = makeUnhealthyConnector("conn-primary [GH-90000]");
        DataConnector fallback = makeHealthyConnector("conn-fallback", List.of("fallback-record [GH-90000]"));

        registry.register(primary); // GH-90000
        registry.register(fallback); // GH-90000
        registry.activate("conn-primary [GH-90000]");
        registry.activate("conn-fallback [GH-90000]");
        registry.setFallback("conn-primary", "conn-fallback"); // GH-90000

        // Since primary is unhealthy(FAILED), read falls back to secondary // GH-90000
        List<String> records = registry.readWithFallback("conn-primary", 10); // GH-90000
        assertThat(records).contains("fallback-record [GH-90000]");
    }

    @Test
    @DisplayName("no fallback configured returns empty list when primary is failed [GH-90000]")
    void noFallbackConfiguredReturnsEmptyOnPrimaryFailure() { // GH-90000
        DataConnector bad = makeUnhealthyConnector("conn-nobak [GH-90000]");
        registry.register(bad); // GH-90000
        registry.activate("conn-nobak [GH-90000]");

        List<String> records = registry.readWithFallback("conn-nobak", 10); // GH-90000
        assertThat(records).isEmpty(); // GH-90000
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DataConnector makeHealthyConnector(String id, List<String> data) { // GH-90000
        return new DataConnector() { // GH-90000
            @Override public String connectorId() { return id; } // GH-90000
            @Override public boolean isHealthy() { return true; } // GH-90000
            @Override public List<String> readBatch(int batchSize) { // GH-90000
                return data.stream().limit(batchSize).toList(); // GH-90000
            }
        };
    }

    private DataConnector makeUnhealthyConnector(String id) { // GH-90000
        return new DataConnector() { // GH-90000
            @Override public String connectorId() { return id; } // GH-90000
            @Override public boolean isHealthy() { return false; } // GH-90000
            @Override public List<String> readBatch(int batchSize) { // GH-90000
                throw new RuntimeException("Unhealthy connector [GH-90000]");
            }
        };
    }

    // ── Connector registry implementation (for tests) ───────────────────────── // GH-90000

    static class ConnectorRegistry {
        private final Map<String, ConnectorState> registry = new HashMap<>(); // GH-90000
        private final Map<String, String> fallbacks = new HashMap<>(); // GH-90000

        void register(DataConnector connector) { // GH-90000
            if (registry.containsKey(connector.connectorId())) { // GH-90000
                throw new IllegalStateException("Connector already registered: " + connector.connectorId()); // GH-90000
            }
            registry.put(connector.connectorId(), // GH-90000
                    new ConnectorState(connector, ConnectorStatus.REGISTERED, null)); // GH-90000
        }

        void activate(String connectorId) { // GH-90000
            ConnectorState state = require(connectorId); // GH-90000
            ConnectorStatus newStatus = state.connector().isHealthy() // GH-90000
                    ? ConnectorStatus.ACTIVE : ConnectorStatus.FAILED;
            String error = newStatus == ConnectorStatus.FAILED ? "Connector health check failed" : null;
            registry.put(connectorId, new ConnectorState(state.connector(), newStatus, error)); // GH-90000
        }

        ConnectorStatus status(String connectorId) { // GH-90000
            return require(connectorId).status(); // GH-90000
        }

        String lastError(String connectorId) { // GH-90000
            return require(connectorId).lastError(); // GH-90000
        }

        List<String> readBatch(String connectorId, int batchSize) { // GH-90000
            ConnectorState state = require(connectorId); // GH-90000
            if (state.status() != ConnectorStatus.ACTIVE) { // GH-90000
                throw new IllegalStateException("Connector is not active: " + connectorId); // GH-90000
            }
            try {
                return state.connector().readBatch(batchSize); // GH-90000
            } catch (Exception e) { // GH-90000
                registry.put(connectorId, // GH-90000
                        new ConnectorState(state.connector(), ConnectorStatus.FAILED, e.getMessage())); // GH-90000
                throw e;
            }
        }

        void setFallback(String primaryId, String fallbackId) { // GH-90000
            fallbacks.put(primaryId, fallbackId); // GH-90000
        }

        List<String> readWithFallback(String connectorId, int batchSize) { // GH-90000
            ConnectorState state = require(connectorId); // GH-90000
            if (state.status() == ConnectorStatus.ACTIVE) { // GH-90000
                try { return state.connector().readBatch(batchSize); } // GH-90000
                catch (Exception ignored) {} // GH-90000
            }
            String fallbackId = fallbacks.get(connectorId); // GH-90000
            if (fallbackId == null) return List.of(); // GH-90000
            ConnectorState fallbackState = registry.get(fallbackId); // GH-90000
            if (fallbackState == null || fallbackState.status() != ConnectorStatus.ACTIVE) return List.of(); // GH-90000
            return fallbackState.connector().readBatch(batchSize); // GH-90000
        }

        private ConnectorState require(String id) { // GH-90000
            ConnectorState s = registry.get(id); // GH-90000
            if (s == null) throw new NoSuchElementException("Connector not found: " + id); // GH-90000
            return s;
        }
    }
}
