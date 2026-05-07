/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("Storage Connector Tests")
class ConnectorTest extends EventloopTestBase {

    // ── Connector model ───────────────────────────────────────────────────────

    enum ConnectorStatus { REGISTERED, ACTIVE, FAILED, DEACTIVATED }

    interface DataConnector {
        String connectorId(); 
        boolean isHealthy(); 
        List<String> readBatch(int batchSize); 
    }

    record ConnectorState(DataConnector connector, ConnectorStatus status, String lastError) {} 

    private ConnectorRegistry registry;

    @BeforeEach
    void setUp() { 
        registry = new ConnectorRegistry(); 
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("registered connector is available in REGISTERED status")
    void registeredConnectorAvailableInRegisteredStatus() { 
        DataConnector conn = makeHealthyConnector("conn-1", List.of("record-1"));
        registry.register(conn); 

        assertThat(registry.status("conn-1")).isEqualTo(ConnectorStatus.REGISTERED);
    }

    @Test
    @DisplayName("registering same connector ID twice is rejected")
    void registeringDuplicateConnectorIdIsRejected() { 
        DataConnector conn = makeHealthyConnector("conn-dup", List.of()); 
        registry.register(conn); 

        assertThatThrownBy(() -> registry.register(conn)) 
                .isInstanceOf(IllegalStateException.class) 
                .hasMessageContaining("already registered");
    }

    // ── Activation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("healthy connector transitions to ACTIVE on activation")
    void healthyConnectorTransitionsToActiveOnActivation() { 
        DataConnector conn = makeHealthyConnector("conn-act", List.of("r1", "r2")); 
        registry.register(conn); 
        registry.activate("conn-act");

        assertThat(registry.status("conn-act")).isEqualTo(ConnectorStatus.ACTIVE);
    }

    @Test
    @DisplayName("activating an unhealthy connector sets status to FAILED")
    void activatingUnhealthyConnectorSetsStatusToFailed() { 
        DataConnector unhealthy = makeUnhealthyConnector("conn-sick");
        registry.register(unhealthy); 
        registry.activate("conn-sick");

        assertThat(registry.status("conn-sick")).isEqualTo(ConnectorStatus.FAILED);
    }

    @Test
    @DisplayName("active connector can read batches of data")
    void activeConnectorCanReadBatches() { 
        DataConnector conn = makeHealthyConnector("conn-read", List.of("a", "b", "c")); 
        registry.register(conn); 
        registry.activate("conn-read");

        List<String> batch = registry.readBatch("conn-read", 2); 
        assertThat(batch).hasSize(2); 
    }

    @Test
    @DisplayName("inactive connector cannot read data")
    void inactiveConnectorCannotReadData() { 
        DataConnector conn = makeHealthyConnector("conn-inactive", List.of("x"));
        registry.register(conn); 

        assertThatThrownBy(() -> registry.readBatch("conn-inactive", 10)) 
                .isInstanceOf(IllegalStateException.class) 
                .hasMessageContaining("not active");
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    @DisplayName("error during read is captured and connector is marked FAILED")
    void errorDuringReadCapturedAndConnectorMarkedFailed() { 
        AtomicInteger callCount = new AtomicInteger(0); 
        DataConnector flakyConn = new DataConnector() { 
            @Override public String connectorId() { return "conn-flaky"; } 
            @Override public boolean isHealthy() { return true; } 
            @Override public List<String> readBatch(int batchSize) { 
                callCount.incrementAndGet(); 
                throw new RuntimeException("Simulated IO error");
            }
        };

        registry.register(flakyConn); 
        registry.activate("conn-flaky");

        try {
            registry.readBatch("conn-flaky", 10); 
        } catch (RuntimeException ignored) {} 

        assertThat(registry.status("conn-flaky")).isEqualTo(ConnectorStatus.FAILED);
        assertThat(registry.lastError("conn-flaky")).contains("Simulated IO error");
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("primary connector failure triggers fallback connector read")
    void primaryFailureTriggersFallback() { 
        DataConnector primary = makeUnhealthyConnector("conn-primary");
        DataConnector fallback = makeHealthyConnector("conn-fallback", List.of("fallback-record"));

        registry.register(primary); 
        registry.register(fallback); 
        registry.activate("conn-primary");
        registry.activate("conn-fallback");
        registry.setFallback("conn-primary", "conn-fallback"); 

        // Since primary is unhealthy(FAILED), read falls back to secondary 
        List<String> records = registry.readWithFallback("conn-primary", 10); 
        assertThat(records).contains("fallback-record");
    }

    @Test
    @DisplayName("no fallback configured returns empty list when primary is failed")
    void noFallbackConfiguredReturnsEmptyOnPrimaryFailure() { 
        DataConnector bad = makeUnhealthyConnector("conn-nobak");
        registry.register(bad); 
        registry.activate("conn-nobak");

        List<String> records = registry.readWithFallback("conn-nobak", 10); 
        assertThat(records).isEmpty(); 
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DataConnector makeHealthyConnector(String id, List<String> data) { 
        return new DataConnector() { 
            @Override public String connectorId() { return id; } 
            @Override public boolean isHealthy() { return true; } 
            @Override public List<String> readBatch(int batchSize) { 
                return data.stream().limit(batchSize).toList(); 
            }
        };
    }

    private DataConnector makeUnhealthyConnector(String id) { 
        return new DataConnector() { 
            @Override public String connectorId() { return id; } 
            @Override public boolean isHealthy() { return false; } 
            @Override public List<String> readBatch(int batchSize) { 
                throw new RuntimeException("Unhealthy connector");
            }
        };
    }

    // ── Connector registry implementation (for tests) ───────────────────────── 

    static class ConnectorRegistry {
        private final Map<String, ConnectorState> registry = new HashMap<>(); 
        private final Map<String, String> fallbacks = new HashMap<>(); 

        void register(DataConnector connector) { 
            if (registry.containsKey(connector.connectorId())) { 
                throw new IllegalStateException("Connector already registered: " + connector.connectorId()); 
            }
            registry.put(connector.connectorId(), 
                    new ConnectorState(connector, ConnectorStatus.REGISTERED, null)); 
        }

        void activate(String connectorId) { 
            ConnectorState state = require(connectorId); 
            ConnectorStatus newStatus = state.connector().isHealthy() 
                    ? ConnectorStatus.ACTIVE : ConnectorStatus.FAILED;
            String error = newStatus == ConnectorStatus.FAILED ? "Connector health check failed" : null;
            registry.put(connectorId, new ConnectorState(state.connector(), newStatus, error)); 
        }

        ConnectorStatus status(String connectorId) { 
            return require(connectorId).status(); 
        }

        String lastError(String connectorId) { 
            return require(connectorId).lastError(); 
        }

        List<String> readBatch(String connectorId, int batchSize) { 
            ConnectorState state = require(connectorId); 
            if (state.status() != ConnectorStatus.ACTIVE) { 
                throw new IllegalStateException("Connector is not active: " + connectorId); 
            }
            try {
                return state.connector().readBatch(batchSize); 
            } catch (Exception e) { 
                registry.put(connectorId, 
                        new ConnectorState(state.connector(), ConnectorStatus.FAILED, e.getMessage())); 
                throw e;
            }
        }

        void setFallback(String primaryId, String fallbackId) { 
            fallbacks.put(primaryId, fallbackId); 
        }

        List<String> readWithFallback(String connectorId, int batchSize) { 
            ConnectorState state = require(connectorId); 
            if (state.status() == ConnectorStatus.ACTIVE) { 
                try { return state.connector().readBatch(batchSize); } 
                catch (Exception ignored) {} 
            }
            String fallbackId = fallbacks.get(connectorId); 
            if (fallbackId == null) return List.of(); 
            ConnectorState fallbackState = registry.get(fallbackId); 
            if (fallbackState == null || fallbackState.status() != ConnectorStatus.ACTIVE) return List.of(); 
            return fallbackState.connector().readBatch(batchSize); 
        }

        private ConnectorState require(String id) { 
            ConnectorState s = registry.get(id); 
            if (s == null) throw new NoSuchElementException("Connector not found: " + id); 
            return s;
        }
    }
}
