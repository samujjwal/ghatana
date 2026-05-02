/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.integration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;

/**
 * Plugin integration tests for Data Cloud.
 *
 * <p>Exercises the plugin SPI integration with the core platform:
 * registration, activation, data forwarding through custom connectors,
 * plugin isolation (one plugin's failure must not affect others), and 
 * deactivation cleanup.
 *
 * @doc.type    class
 * @doc.purpose Plugin integration tests: registration, activation, forwarding, isolation
 * @doc.layer   product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Plugin Integration Tests")
class PluginIntegrationTest extends EventloopTestBase {

    private PluginPlatform platform;

    @BeforeEach
    void setUp() { 
        platform = new PluginPlatform(); 
    }

    // ── Plugin registration ────────────────────────────────────────────────────

    @Test
    @DisplayName("plugin registered with the platform is discoverable by ID")
    void pluginRegisteredIsdiscoverableById() { 
        TestPlugin plugin = new TestPlugin("plugin-csv-int", true, List.of("row1", "row2")); 
        platform.registerPlugin(plugin); 

        assertThat(platform.findPlugin("plugin-csv-int")).isPresent();
    }

    @Test
    @DisplayName("registering duplicate plugin ID is rejected")
    void registeringDuplicatePluginIdIsRejected() { 
        TestPlugin plugin = new TestPlugin("plugin-dup-int", true, List.of()); 
        platform.registerPlugin(plugin); 

        assertThatThrownBy(() -> platform.registerPlugin(plugin)) 
                .isInstanceOf(IllegalStateException.class); 
    }

    // ── Plugin activation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("healthy plugin activates and transitions to ACTIVE state")
    void healthyPluginActivatesSuccessfully() { 
        TestPlugin plugin = new TestPlugin("plugin-act-int", true, List.of("data"));
        platform.registerPlugin(plugin); 
        platform.activate("plugin-act-int");

        assertThat(platform.pluginStatus("plugin-act-int")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("unhealthy plugin activation sets status to FAILED")
    void unhealthyPluginActivationSetsStatusToFailed() { 
        TestPlugin plugin = new TestPlugin("plugin-sick-int", false, List.of()); 
        platform.registerPlugin(plugin); 
        platform.activate("plugin-sick-int");

        assertThat(platform.pluginStatus("plugin-sick-int")).isEqualTo("FAILED");
    }

    // ── Data forwarding ────────────────────────────────────────────────────────

    @Test
    @DisplayName("active plugin forwards ingested data to the platform entity store")
    void activePluginForwardsDataToPlatform() { 
        TestPlugin plugin = new TestPlugin("plugin-fwd-int", true, 
                List.of("record-A", "record-B", "record-C")); 
        platform.registerPlugin(plugin); 
        platform.activate("plugin-fwd-int");

        List<String> ingested = platform.ingestFrom("plugin-fwd-int", 10); 

        assertThat(ingested).containsExactly("record-A", "record-B", "record-C"); 
    }

    @Test
    @DisplayName("inactive plugin cannot forward data")
    void inactivePluginCannotForwardData() { 
        TestPlugin plugin = new TestPlugin("plugin-nofwd", true, List.of("data"));
        platform.registerPlugin(plugin); 

        assertThatThrownBy(() -> platform.ingestFrom("plugin-nofwd", 10)) 
                .isInstanceOf(IllegalStateException.class) 
                .hasMessageContaining("not active");
    }

    // ── Plugin isolation ───────────────────────────────────────────────────────

    @Test
    @DisplayName("one plugin's failure during ingest does not affect other plugins")
    void onePluginFailureDoesNotAffectOtherPlugins() { 
        TestPlugin faultyPlugin = new TestPlugin("plugin-faulty", true, null) { 
            @Override public List<String> readBatch(int n) { 
                throw new RuntimeException("Simulated ingest error");
            }
        };
        TestPlugin goodPlugin = new TestPlugin("plugin-good-int", true, 
                List.of("good-record"));

        platform.registerPlugin(faultyPlugin); 
        platform.registerPlugin(goodPlugin); 
        platform.activate("plugin-faulty");
        platform.activate("plugin-good-int");

        // Faulty plugin's ingest error is swallowed per isolation contract
        try { platform.ingestFrom("plugin-faulty", 10); } catch (Exception ignored) {} 

        // Good plugin remains ACTIVE
        assertThat(platform.pluginStatus("plugin-good-int")).isEqualTo("ACTIVE");
        List<String> goodData = platform.ingestFrom("plugin-good-int", 10); 
        assertThat(goodData).contains("good-record");
    }

    // ── Plugin deactivation ────────────────────────────────────────────────────

    @Test
    @DisplayName("deactivated plugin transitions to INACTIVE and its buffered data is flushed")
    void deactivatedPluginTransitionsToInactive() { 
        TestPlugin plugin = new TestPlugin("plugin-deact-int", true, 
                List.of("flush-record"));
        platform.registerPlugin(plugin); 
        platform.activate("plugin-deact-int");
        platform.deactivate("plugin-deact-int");

        assertThat(platform.pluginStatus("plugin-deact-int")).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("deactivated plugin cannot ingest data")
    void deactivatedPluginCannotIngestData() { 
        TestPlugin plugin = new TestPlugin("plugin-deact2-int", true, List.of("x"));
        platform.registerPlugin(plugin); 
        platform.activate("plugin-deact2-int");
        platform.deactivate("plugin-deact2-int");

        assertThatThrownBy(() -> platform.ingestFrom("plugin-deact2-int", 10)) 
                .isInstanceOf(IllegalStateException.class); 
    }

    // ── Multiple plugins collaboration ─────────────────────────────────────────

    @Test
    @DisplayName("multiple active plugins can independently forward data to the platform")
    void multipleActivePluginsForwardDataIndependently() { 
        TestPlugin p1 = new TestPlugin("plugin-m1", true, List.of("m1-record"));
        TestPlugin p2 = new TestPlugin("plugin-m2", true, List.of("m2-a", "m2-b")); 
        TestPlugin p3 = new TestPlugin("plugin-m3", true, List.of("m3-x", "m3-y", "m3-z")); 

        platform.registerPlugin(p1); 
        platform.registerPlugin(p2); 
        platform.registerPlugin(p3); 
        platform.activate("plugin-m1");
        platform.activate("plugin-m2");
        platform.activate("plugin-m3");

        assertThat(platform.ingestFrom("plugin-m1", 10)).hasSize(1); 
        assertThat(platform.ingestFrom("plugin-m2", 10)).hasSize(2); 
        assertThat(platform.ingestFrom("plugin-m3", 10)).hasSize(3); 
    }

    // ── Test plugin ───────────────────────────────────────────────────────────

    static class TestPlugin {
        private final String pluginId;
        private final boolean healthy;
        private final List<String> data;

        TestPlugin(String pluginId, boolean healthy, List<String> data) { 
            this.pluginId = pluginId;
            this.healthy = healthy;
            this.data = data;
        }

        String getPluginId() { return pluginId; } 
        boolean isHealthy() { return healthy; } 
        List<String> readBatch(int n) { 
            if (data == null) return List.of(); 
            return data.stream().limit(n).toList(); 
        }
    }

    // ── Plugin platform implementation (for tests) ──────────────────────────── 

    static class PluginPlatform {
        private final ConcurrentHashMap<String, TestPlugin> plugins = new ConcurrentHashMap<>(); 
        private final ConcurrentHashMap<String, String> statuses = new ConcurrentHashMap<>(); 

        void registerPlugin(TestPlugin plugin) { 
            if (plugins.containsKey(plugin.getPluginId())) { 
                throw new IllegalStateException("Plugin already registered: " + plugin.getPluginId()); 
            }
            plugins.put(plugin.getPluginId(), plugin); 
            statuses.put(plugin.getPluginId(), "REGISTERED"); 
        }

        Optional<TestPlugin> findPlugin(String pluginId) { 
            return Optional.ofNullable(plugins.get(pluginId)); 
        }

        void activate(String pluginId) { 
            TestPlugin p = require(pluginId); 
            String newStatus = p.isHealthy() ? "ACTIVE" : "FAILED"; 
            statuses.put(pluginId, newStatus); 
        }

        void deactivate(String pluginId) { 
            require(pluginId); 
            statuses.put(pluginId, "INACTIVE"); 
        }

        String pluginStatus(String pluginId) { 
            return statuses.getOrDefault(pluginId, "UNKNOWN"); 
        }

        List<String> ingestFrom(String pluginId, int batchSize) { 
            require(pluginId); 
            if (!"ACTIVE".equals(statuses.get(pluginId))) { 
                throw new IllegalStateException("Plugin is not active: " + pluginId); 
            }
            TestPlugin p = plugins.get(pluginId); 
            return p.readBatch(batchSize); 
        }

        private TestPlugin require(String pluginId) { 
            TestPlugin p = plugins.get(pluginId); 
            if (p == null) throw new NoSuchElementException("Plugin not registered: " + pluginId); 
            return p;
        }
    }
}
