/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 */

package com.ghatana.config.runtime.engine;

import com.ghatana.platform.core.exception.ConfigurationException;
import io.activej.eventloop.Eventloop;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ConfigurationEngine} — the unified configuration facade.
 *
 * Covers:
 * - Loading single configs and directories
 * - Config type inference from directory structure
 * - Schema validation (when schema exists) // GH-90000
 * - Variable interpolation
 * - Config versioning (snapshot, list versions, getCurrentVersion) // GH-90000
 * - Config rollback
 * - Change listener notification
 * - Lifecycle (stop) // GH-90000
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // GH-90000
class ConfigurationEngineTest {

    @TempDir
    Path tempDir;

    private ConfigurationEngine engine;

    @BeforeEach
    void setUp() { // GH-90000
        engine = ConfigurationEngine.builder(tempDir) // GH-90000
                .variable("ENV", "test") // GH-90000
                .variable("APP_NAME", "ghatana") // GH-90000
                .build(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (engine != null) { // GH-90000
            // synchronous cleanup
            engine.stop(); // GH-90000
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Loading
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1) // GH-90000
    @DisplayName("loadConfig — loads a YAML file and registers it [GH-90000]")
    void loadConfig_registersConfig() throws Exception { // GH-90000
        Path yaml = writeYaml(tempDir, "my-config.yaml", // GH-90000
                "name: test-config\nlevel: INFO\n");

        Eventloop eventloop = Eventloop.builder().build(); // GH-90000
        eventloop.submit(() -> // GH-90000
                engine.loadConfig("my-config", yaml, ConfigType.SYSTEM) // GH-90000
                        .whenResult(source -> { // GH-90000
                            assertNotNull(source); // GH-90000
                            assertEquals("test-config", source.getString("name [GH-90000]").orElse(null));
                            assertEquals("INFO", source.getString("level [GH-90000]").orElse(null));
                        })
                        .whenException(e -> fail("Should not fail: " + e))); // GH-90000
        eventloop.run(); // GH-90000

        assertTrue(engine.hasConfig("my-config [GH-90000]"));
        assertEquals(1, engine.size()); // GH-90000
    }

    @Test
    @Order(2) // GH-90000
    @DisplayName("loadConfig — creates a version snapshot [GH-90000]")
    void loadConfig_createsVersionSnapshot() throws Exception { // GH-90000
        Path yaml = writeYaml(tempDir, "versioned.yaml", // GH-90000
                "version: 1\nkey: value\n");

        Eventloop eventloop = Eventloop.builder().build(); // GH-90000
        eventloop.submit(() -> // GH-90000
                engine.loadConfig("versioned", yaml, ConfigType.SYSTEM) // GH-90000
                        .whenResult(source -> { // GH-90000
                            assertEquals("v1", engine.getCurrentVersion("versioned [GH-90000]"));
                            assertEquals(List.of("v1 [GH-90000]"), engine.listVersions("versioned [GH-90000]"));
                        })
                        .whenException(e -> fail("Should not fail: " + e))); // GH-90000
        eventloop.run(); // GH-90000
    }

    @Test
    @Order(3) // GH-90000
    @DisplayName("loadConfig — reloading with changed content bumps version [GH-90000]")
    void loadConfig_reloadBumpsVersion() throws Exception { // GH-90000
        Path yaml = writeYaml(tempDir, "bumping.yaml", "version: 1\n"); // GH-90000

        Eventloop eventloop = Eventloop.builder().build(); // GH-90000
        eventloop.submit(() -> // GH-90000
                engine.loadConfig("bumping", yaml, ConfigType.SYSTEM) // GH-90000
                        .then($ -> { // GH-90000
                            try {
                                Files.writeString(yaml, "version: 2\n"); // GH-90000
                            } catch (IOException e) { // GH-90000
                                throw new RuntimeException(e); // GH-90000
                            }
                            return engine.loadConfig("bumping", yaml, ConfigType.SYSTEM); // GH-90000
                        })
                        .whenResult(source -> { // GH-90000
                            assertEquals("v2", engine.getCurrentVersion("bumping [GH-90000]"));
                            assertEquals(List.of("v2", "v1"), engine.listVersions("bumping [GH-90000]"));
                        })
                        .whenException(e -> fail("Should not fail: " + e))); // GH-90000
        eventloop.run(); // GH-90000
    }

    @Test
    @Order(4) // GH-90000
    @DisplayName("loadConfig — unchanged content does not bump version [GH-90000]")
    void loadConfig_unchangedContentNoBump() throws Exception { // GH-90000
        Path yaml = writeYaml(tempDir, "stable.yaml", "stable: true\n"); // GH-90000

        Eventloop eventloop = Eventloop.builder().build(); // GH-90000
        eventloop.submit(() -> // GH-90000
                engine.loadConfig("stable", yaml, ConfigType.SYSTEM) // GH-90000
                        .then($ -> engine.loadConfig("stable", yaml, ConfigType.SYSTEM)) // GH-90000
                        .whenResult(source -> { // GH-90000
                            assertEquals("v1", engine.getCurrentVersion("stable [GH-90000]"));
                            assertEquals(1, engine.listVersions("stable [GH-90000]").size());
                        })
                        .whenException(e -> fail("Should not fail: " + e))); // GH-90000
        eventloop.run(); // GH-90000
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Directory Loading
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(10) // GH-90000
    @DisplayName("loadDirectory — loads all YAML files recursively [GH-90000]")
    void loadDirectory_loadsAll() throws Exception { // GH-90000
        // Create structure:
        // agents/
        //   fraud-detector.yaml
        // pipelines/
        //   ingestion.yml
        // system.yaml
        Path agentsDir = tempDir.resolve("agents [GH-90000]");
        Files.createDirectories(agentsDir); // GH-90000
        writeYaml(agentsDir, "fraud-detector.yaml", // GH-90000
                "type: deterministic\nname: fraud-detector\n");

        Path pipelinesDir = tempDir.resolve("pipelines [GH-90000]");
        Files.createDirectories(pipelinesDir); // GH-90000
        writeYaml(pipelinesDir, "ingestion.yml", // GH-90000
                "stages:\n  - name: ingest\n");

        writeYaml(tempDir, "system.yaml", // GH-90000
                "logging:\n  level: DEBUG\n");

        Eventloop eventloop = Eventloop.builder().build(); // GH-90000
        eventloop.submit(() -> // GH-90000
                engine.loadDirectory(tempDir) // GH-90000
                        .whenResult($ -> { // GH-90000
                            assertEquals(3, engine.size()); // GH-90000
                            assertTrue(engine.hasConfig("agents.fraud-detector [GH-90000]"));
                            assertTrue(engine.hasConfig("pipelines.ingestion [GH-90000]"));
                            assertTrue(engine.hasConfig("system [GH-90000]"));
                        })
                        .whenException(e -> fail("Should not fail: " + e))); // GH-90000
        eventloop.run(); // GH-90000
    }

    @Test
    @Order(11) // GH-90000
    @DisplayName("loadDirectory — fails for non-directory path [GH-90000]")
    void loadDirectory_failsNonDir() throws Exception { // GH-90000
        Path file = writeYaml(tempDir, "not-a-dir.yaml", "x: 1\n"); // GH-90000

        Eventloop eventloop = Eventloop.builder().build(); // GH-90000
        eventloop.submit(() -> // GH-90000
                engine.loadDirectory(file) // GH-90000
                        .whenResult($ -> fail("Should fail [GH-90000]"))
                        .whenException(e -> // GH-90000
                                assertInstanceOf(ConfigurationException.class, e))); // GH-90000
        eventloop.run(); // GH-90000
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Interpolation
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(20) // GH-90000
    @DisplayName("interpolate — resolves registered variables [GH-90000]")
    void interpolate_resolvesVariables() { // GH-90000
        assertEquals("test", engine.interpolate("${ENV} [GH-90000]"));
        assertEquals("ghatana", engine.interpolate("${APP_NAME} [GH-90000]"));
        assertEquals("running in test", engine.interpolate("running in ${ENV} [GH-90000]"));
    }

    @Test
    @Order(21) // GH-90000
    @DisplayName("interpolate — uses defaults for missing variables [GH-90000]")
    void interpolate_usesDefaults() { // GH-90000
        assertEquals("fallback", engine.interpolate("${MISSING:fallback} [GH-90000]"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Versioning
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(30) // GH-90000
    @DisplayName("getCurrentVersion — returns v0 for unknown config [GH-90000]")
    void getCurrentVersion_unknown() { // GH-90000
        assertEquals("v0", engine.getCurrentVersion("nonexistent [GH-90000]"));
    }

    @Test
    @Order(31) // GH-90000
    @DisplayName("listVersions — returns empty for unknown config [GH-90000]")
    void listVersions_unknown() { // GH-90000
        assertTrue(engine.listVersions("nonexistent [GH-90000]").isEmpty());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Rollback
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(40) // GH-90000
    @DisplayName("rollback — restores to previous version [GH-90000]")
    void rollback_restoresVersion() throws Exception { // GH-90000
        Path yaml = writeYaml(tempDir, "rollback-test.yaml", "key: v1\n"); // GH-90000

        Eventloop eventloop = Eventloop.builder().build(); // GH-90000
        eventloop.submit(() -> // GH-90000
                engine.loadConfig("rollback-test", yaml, ConfigType.SYSTEM) // GH-90000
                        .then($ -> { // GH-90000
                            try {
                                Files.writeString(yaml, "key: v2\n"); // GH-90000
                            } catch (IOException e) { // GH-90000
                                throw new RuntimeException(e); // GH-90000
                            }
                            return engine.loadConfig("rollback-test", yaml, ConfigType.SYSTEM); // GH-90000
                        })
                        .then($ -> { // GH-90000
                            assertEquals("v2", engine.getCurrentVersion("rollback-test [GH-90000]"));
                            return engine.rollback("rollback-test", "v1"); // GH-90000
                        })
                        .whenResult($ -> // GH-90000
                                // After rollback, current version is v3 (rollback creates new snapshot) // GH-90000
                                assertEquals("v3", engine.getCurrentVersion("rollback-test [GH-90000]")))
                        .whenException(e -> fail("Should not fail: " + e))); // GH-90000
        eventloop.run(); // GH-90000
    }

    @Test
    @Order(41) // GH-90000
    @DisplayName("rollback — fails for unknown version [GH-90000]")
    void rollback_failsUnknownVersion() throws Exception { // GH-90000
        Path yaml = writeYaml(tempDir, "rollback-fail.yaml", "k: v\n"); // GH-90000

        Eventloop eventloop = Eventloop.builder().build(); // GH-90000
        eventloop.submit(() -> // GH-90000
                engine.loadConfig("rollback-fail", yaml, ConfigType.SYSTEM) // GH-90000
                        .then($ -> engine.rollback("rollback-fail", "v99")) // GH-90000
                        .whenResult($ -> fail("Should fail [GH-90000]"))
                        .whenException(e -> // GH-90000
                                assertInstanceOf(ConfigurationException.class, e))); // GH-90000
        eventloop.run(); // GH-90000
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Change Listeners
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(50) // GH-90000
    @DisplayName("onConfigChange — listener receives rollback events [GH-90000]")
    void onConfigChange_rollbackEvent() throws Exception { // GH-90000
        Path yaml = writeYaml(tempDir, "listener-test.yaml", "state: original\n"); // GH-90000

        AtomicReference<ConfigChange> received = new AtomicReference<>(); // GH-90000
        engine.onConfigChange(received::set); // GH-90000

        Eventloop eventloop = Eventloop.builder().build(); // GH-90000
        eventloop.submit(() -> // GH-90000
                engine.loadConfig("listener-test", yaml, ConfigType.SYSTEM) // GH-90000
                        .then($ -> { // GH-90000
                            try {
                                Files.writeString(yaml, "state: modified\n"); // GH-90000
                            } catch (IOException e) { // GH-90000
                                throw new RuntimeException(e); // GH-90000
                            }
                            return engine.loadConfig("listener-test", yaml, ConfigType.SYSTEM); // GH-90000
                        })
                        .then($ -> engine.rollback("listener-test", "v1")) // GH-90000
                        .whenException(e -> fail("Should not fail: " + e))); // GH-90000
        eventloop.run(); // GH-90000

        ConfigChange change = received.get(); // GH-90000
        assertNotNull(change); // GH-90000
        assertEquals("listener-test", change.configName()); // GH-90000
        assertEquals(ConfigChange.ChangeType.ROLLED_BACK, change.changeType()); // GH-90000
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ConfigType
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(60) // GH-90000
    @DisplayName("ConfigType — fromDirectoryName resolves correctly [GH-90000]")
    void configType_fromDirectoryName() { // GH-90000
        assertEquals(ConfigType.AGENT, ConfigType.fromDirectoryName("agents [GH-90000]"));
        assertEquals(ConfigType.PIPELINE, ConfigType.fromDirectoryName("pipelines [GH-90000]"));
        assertEquals(ConfigType.SYSTEM, ConfigType.fromDirectoryName("system [GH-90000]"));
        assertEquals(ConfigType.SYSTEM, ConfigType.fromDirectoryName("unknown [GH-90000]"));
        assertEquals(ConfigType.SYSTEM, ConfigType.fromDirectoryName(null)); // GH-90000
        assertEquals(ConfigType.SYSTEM, ConfigType.fromDirectoryName(" [GH-90000]"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(70) // GH-90000
    @DisplayName("stop — clears registry [GH-90000]")
    void stop_clearsAll() throws Exception { // GH-90000
        Path yaml = writeYaml(tempDir, "stop-test.yaml", "x: 1\n"); // GH-90000

        Eventloop eventloop = Eventloop.builder().build(); // GH-90000
        eventloop.submit(() -> // GH-90000
                engine.loadConfig("stop-test", yaml, ConfigType.SYSTEM) // GH-90000
                        .then($ -> engine.stop()) // GH-90000
                        .whenResult($ -> assertEquals(0, engine.size())) // GH-90000
                        .whenException(e -> fail("Should not fail: " + e))); // GH-90000
        eventloop.run(); // GH-90000
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Registry Access
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(80) // GH-90000
    @DisplayName("getConfig — returns empty for missing config [GH-90000]")
    void getConfig_empty() { // GH-90000
        assertTrue(engine.getConfig("missing [GH-90000]").isEmpty());
    }

    @Test
    @Order(81) // GH-90000
    @DisplayName("listConfigNames — initially empty [GH-90000]")
    void listConfigNames_empty() { // GH-90000
        assertTrue(engine.listConfigNames().isEmpty()); // GH-90000
    }

    @Test
    @Order(82) // GH-90000
    @DisplayName("hasConfig — false for unloaded [GH-90000]")
    void hasConfig_false() { // GH-90000
        assertFalse(engine.hasConfig("ghost [GH-90000]"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ConfigVersionStore
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(90) // GH-90000
    @DisplayName("ConfigVersionStore — snapshot and retrieve content [GH-90000]")
    void versionStore_snapshotAndRetrieve() throws Exception { // GH-90000
        Path versionDir = tempDir.resolve("versions [GH-90000]");
        ConfigVersionStore store = new ConfigVersionStore(versionDir); // GH-90000

        Path yaml = writeYaml(tempDir, "store-test.yaml", "content: original\n"); // GH-90000
        String v1 = store.snapshot("test-cfg", yaml); // GH-90000
        assertEquals("v1", v1); // GH-90000

        // Change content
        Files.writeString(yaml, "content: updated\n"); // GH-90000
        String v2 = store.snapshot("test-cfg", yaml); // GH-90000
        assertEquals("v2", v2); // GH-90000

        assertEquals("v2", store.getCurrentVersion("test-cfg [GH-90000]"));
        assertEquals(List.of("v2", "v1"), store.listVersions("test-cfg [GH-90000]"));
        assertEquals(2, store.totalSnapshots()); // GH-90000

        // Read version content
        assertTrue(store.getVersionContent("test-cfg", "v1").isPresent()); // GH-90000
        assertTrue(store.getVersionContent("test-cfg", "v1").get().contains("original [GH-90000]"));
        assertTrue(store.getVersionContent("test-cfg", "v2").get().contains("updated [GH-90000]"));
    }

    @Test
    @Order(91) // GH-90000
    @DisplayName("ConfigVersionStore — rollback returns correct path [GH-90000]")
    void versionStore_rollback() throws Exception { // GH-90000
        Path versionDir = tempDir.resolve("versions2 [GH-90000]");
        ConfigVersionStore store = new ConfigVersionStore(versionDir); // GH-90000

        Path yaml = writeYaml(tempDir, "rb-test.yaml", "v: 1\n"); // GH-90000
        store.snapshot("rb", yaml); // GH-90000
        Files.writeString(yaml, "v: 2\n"); // GH-90000
        store.snapshot("rb", yaml); // GH-90000

        var restored = store.rollback("rb", "v1"); // GH-90000
        assertTrue(restored.isPresent()); // GH-90000
        assertEquals("v3", store.getCurrentVersion("rb [GH-90000]"));
    }

    @Test
    @Order(92) // GH-90000
    @DisplayName("ConfigVersionStore — skips snapshot for unchanged content [GH-90000]")
    void versionStore_skipUnchanged() throws Exception { // GH-90000
        Path versionDir = tempDir.resolve("versions3 [GH-90000]");
        ConfigVersionStore store = new ConfigVersionStore(versionDir); // GH-90000

        Path yaml = writeYaml(tempDir, "dup-test.yaml", "same: content\n"); // GH-90000
        store.snapshot("dup", yaml); // GH-90000
        String v = store.snapshot("dup", yaml); // same content // GH-90000
        assertEquals("v1", v); // no bump // GH-90000
        assertEquals(1, store.totalSnapshots()); // GH-90000
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ConfigChange
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(100) // GH-90000
    @DisplayName("ConfigChange — record accessors work [GH-90000]")
    void configChange_recordAccessors() { // GH-90000
        ConfigChange change = new ConfigChange("test", ConfigChange.ChangeType.MODIFIED, "v2"); // GH-90000
        assertEquals("test", change.configName()); // GH-90000
        assertEquals(ConfigChange.ChangeType.MODIFIED, change.changeType()); // GH-90000
        assertEquals("v2", change.version()); // GH-90000
        assertNotNull(change.timestamp()); // GH-90000
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    private static Path writeYaml(Path dir, String filename, String content) // GH-90000
            throws IOException {
        Path file = dir.resolve(filename); // GH-90000
        Files.writeString(file, content); // GH-90000
        return file;
    }
}
