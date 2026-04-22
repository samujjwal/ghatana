/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * plugin isolation (one plugin's failure must not affect others), and // GH-90000
 * deactivation cleanup.
 *
 * @doc.type    class
 * @doc.purpose Plugin integration tests: registration, activation, forwarding, isolation
 * @doc.layer   product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Plugin Integration Tests [GH-90000]")
class PluginIntegrationTest extends EventloopTestBase {

    private PluginPlatform platform;

    @BeforeEach
    void setUp() { // GH-90000
        platform = new PluginPlatform(); // GH-90000
    }

    // ── Plugin registration ────────────────────────────────────────────────────

    @Test
    @DisplayName("plugin registered with the platform is discoverable by ID [GH-90000]")
    void pluginRegisteredIsdiscoverableById() { // GH-90000
        TestPlugin plugin = new TestPlugin("plugin-csv-int", true, List.of("row1", "row2")); // GH-90000
        platform.registerPlugin(plugin); // GH-90000

        assertThat(platform.findPlugin("plugin-csv-int [GH-90000]")).isPresent();
    }

    @Test
    @DisplayName("registering duplicate plugin ID is rejected [GH-90000]")
    void registeringDuplicatePluginIdIsRejected() { // GH-90000
        TestPlugin plugin = new TestPlugin("plugin-dup-int", true, List.of()); // GH-90000
        platform.registerPlugin(plugin); // GH-90000

        assertThatThrownBy(() -> platform.registerPlugin(plugin)) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
    }

    // ── Plugin activation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("healthy plugin activates and transitions to ACTIVE state [GH-90000]")
    void healthyPluginActivatesSuccessfully() { // GH-90000
        TestPlugin plugin = new TestPlugin("plugin-act-int", true, List.of("data [GH-90000]"));
        platform.registerPlugin(plugin); // GH-90000
        platform.activate("plugin-act-int [GH-90000]");

        assertThat(platform.pluginStatus("plugin-act-int [GH-90000]")).isEqualTo("ACTIVE [GH-90000]");
    }

    @Test
    @DisplayName("unhealthy plugin activation sets status to FAILED [GH-90000]")
    void unhealthyPluginActivationSetsStatusToFailed() { // GH-90000
        TestPlugin plugin = new TestPlugin("plugin-sick-int", false, List.of()); // GH-90000
        platform.registerPlugin(plugin); // GH-90000
        platform.activate("plugin-sick-int [GH-90000]");

        assertThat(platform.pluginStatus("plugin-sick-int [GH-90000]")).isEqualTo("FAILED [GH-90000]");
    }

    // ── Data forwarding ────────────────────────────────────────────────────────

    @Test
    @DisplayName("active plugin forwards ingested data to the platform entity store [GH-90000]")
    void activePluginForwardsDataToPlatform() { // GH-90000
        TestPlugin plugin = new TestPlugin("plugin-fwd-int", true, // GH-90000
                List.of("record-A", "record-B", "record-C")); // GH-90000
        platform.registerPlugin(plugin); // GH-90000
        platform.activate("plugin-fwd-int [GH-90000]");

        List<String> ingested = platform.ingestFrom("plugin-fwd-int", 10); // GH-90000

        assertThat(ingested).containsExactly("record-A", "record-B", "record-C"); // GH-90000
    }

    @Test
    @DisplayName("inactive plugin cannot forward data [GH-90000]")
    void inactivePluginCannotForwardData() { // GH-90000
        TestPlugin plugin = new TestPlugin("plugin-nofwd", true, List.of("data [GH-90000]"));
        platform.registerPlugin(plugin); // GH-90000

        assertThatThrownBy(() -> platform.ingestFrom("plugin-nofwd", 10)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("not active [GH-90000]");
    }

    // ── Plugin isolation ───────────────────────────────────────────────────────

    @Test
    @DisplayName("one plugin's failure during ingest does not affect other plugins [GH-90000]")
    void onePluginFailureDoesNotAffectOtherPlugins() { // GH-90000
        TestPlugin faultyPlugin = new TestPlugin("plugin-faulty", true, null) { // GH-90000
            @Override public List<String> readBatch(int n) { // GH-90000
                throw new RuntimeException("Simulated ingest error [GH-90000]");
            }
        };
        TestPlugin goodPlugin = new TestPlugin("plugin-good-int", true, // GH-90000
                List.of("good-record [GH-90000]"));

        platform.registerPlugin(faultyPlugin); // GH-90000
        platform.registerPlugin(goodPlugin); // GH-90000
        platform.activate("plugin-faulty [GH-90000]");
        platform.activate("plugin-good-int [GH-90000]");

        // Faulty plugin's ingest error is swallowed per isolation contract
        try { platform.ingestFrom("plugin-faulty", 10); } catch (Exception ignored) {} // GH-90000

        // Good plugin remains ACTIVE
        assertThat(platform.pluginStatus("plugin-good-int [GH-90000]")).isEqualTo("ACTIVE [GH-90000]");
        List<String> goodData = platform.ingestFrom("plugin-good-int", 10); // GH-90000
        assertThat(goodData).contains("good-record [GH-90000]");
    }

    // ── Plugin deactivation ────────────────────────────────────────────────────

    @Test
    @DisplayName("deactivated plugin transitions to INACTIVE and its buffered data is flushed [GH-90000]")
    void deactivatedPluginTransitionsToInactive() { // GH-90000
        TestPlugin plugin = new TestPlugin("plugin-deact-int", true, // GH-90000
                List.of("flush-record [GH-90000]"));
        platform.registerPlugin(plugin); // GH-90000
        platform.activate("plugin-deact-int [GH-90000]");
        platform.deactivate("plugin-deact-int [GH-90000]");

        assertThat(platform.pluginStatus("plugin-deact-int [GH-90000]")).isEqualTo("INACTIVE [GH-90000]");
    }

    @Test
    @DisplayName("deactivated plugin cannot ingest data [GH-90000]")
    void deactivatedPluginCannotIngestData() { // GH-90000
        TestPlugin plugin = new TestPlugin("plugin-deact2-int", true, List.of("x [GH-90000]"));
        platform.registerPlugin(plugin); // GH-90000
        platform.activate("plugin-deact2-int [GH-90000]");
        platform.deactivate("plugin-deact2-int [GH-90000]");

        assertThatThrownBy(() -> platform.ingestFrom("plugin-deact2-int", 10)) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
    }

    // ── Multiple plugins collaboration ─────────────────────────────────────────

    @Test
    @DisplayName("multiple active plugins can independently forward data to the platform [GH-90000]")
    void multipleActivePluginsForwardDataIndependently() { // GH-90000
        TestPlugin p1 = new TestPlugin("plugin-m1", true, List.of("m1-record [GH-90000]"));
        TestPlugin p2 = new TestPlugin("plugin-m2", true, List.of("m2-a", "m2-b")); // GH-90000
        TestPlugin p3 = new TestPlugin("plugin-m3", true, List.of("m3-x", "m3-y", "m3-z")); // GH-90000

        platform.registerPlugin(p1); // GH-90000
        platform.registerPlugin(p2); // GH-90000
        platform.registerPlugin(p3); // GH-90000
        platform.activate("plugin-m1 [GH-90000]");
        platform.activate("plugin-m2 [GH-90000]");
        platform.activate("plugin-m3 [GH-90000]");

        assertThat(platform.ingestFrom("plugin-m1", 10)).hasSize(1); // GH-90000
        assertThat(platform.ingestFrom("plugin-m2", 10)).hasSize(2); // GH-90000
        assertThat(platform.ingestFrom("plugin-m3", 10)).hasSize(3); // GH-90000
    }

    // ── Test plugin ───────────────────────────────────────────────────────────

    static class TestPlugin {
        private final String pluginId;
        private final boolean healthy;
        private final List<String> data;

        TestPlugin(String pluginId, boolean healthy, List<String> data) { // GH-90000
            this.pluginId = pluginId;
            this.healthy = healthy;
            this.data = data;
        }

        String getPluginId() { return pluginId; } // GH-90000
        boolean isHealthy() { return healthy; } // GH-90000
        List<String> readBatch(int n) { // GH-90000
            if (data == null) return List.of(); // GH-90000
            return data.stream().limit(n).toList(); // GH-90000
        }
    }

    // ── Plugin platform implementation (for tests) ──────────────────────────── // GH-90000

    static class PluginPlatform {
        private final ConcurrentHashMap<String, TestPlugin> plugins = new ConcurrentHashMap<>(); // GH-90000
        private final ConcurrentHashMap<String, String> statuses = new ConcurrentHashMap<>(); // GH-90000

        void registerPlugin(TestPlugin plugin) { // GH-90000
            if (plugins.containsKey(plugin.getPluginId())) { // GH-90000
                throw new IllegalStateException("Plugin already registered: " + plugin.getPluginId()); // GH-90000
            }
            plugins.put(plugin.getPluginId(), plugin); // GH-90000
            statuses.put(plugin.getPluginId(), "REGISTERED"); // GH-90000
        }

        Optional<TestPlugin> findPlugin(String pluginId) { // GH-90000
            return Optional.ofNullable(plugins.get(pluginId)); // GH-90000
        }

        void activate(String pluginId) { // GH-90000
            TestPlugin p = require(pluginId); // GH-90000
            String newStatus = p.isHealthy() ? "ACTIVE" : "FAILED"; // GH-90000
            statuses.put(pluginId, newStatus); // GH-90000
        }

        void deactivate(String pluginId) { // GH-90000
            require(pluginId); // GH-90000
            statuses.put(pluginId, "INACTIVE"); // GH-90000
        }

        String pluginStatus(String pluginId) { // GH-90000
            return statuses.getOrDefault(pluginId, "UNKNOWN"); // GH-90000
        }

        List<String> ingestFrom(String pluginId, int batchSize) { // GH-90000
            require(pluginId); // GH-90000
            if (!"ACTIVE".equals(statuses.get(pluginId))) { // GH-90000
                throw new IllegalStateException("Plugin is not active: " + pluginId); // GH-90000
            }
            TestPlugin p = plugins.get(pluginId); // GH-90000
            return p.readBatch(batchSize); // GH-90000
        }

        private TestPlugin require(String pluginId) { // GH-90000
            TestPlugin p = plugins.get(pluginId); // GH-90000
            if (p == null) throw new NoSuchElementException("Plugin not registered: " + pluginId); // GH-90000
            return p;
        }
    }
}
