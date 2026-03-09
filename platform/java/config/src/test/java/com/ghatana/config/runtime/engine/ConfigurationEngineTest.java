/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.config.runtime.engine;

import com.ghatana.config.runtime.validation.ValidationResult;
import io.activej.eventloop.Eventloop;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ConfigurationEngine} — the unified configuration facade.
 *
 * Covers:
 * - Loading single configs and directories
 * - Config type inference from directory structure
 * - Schema validation (when schema exists)
 * - Variable interpolation
 * - Config versioning (snapshot, list versions, getCurrentVersion)
 * - Config rollback
 * - Change listener notification
 * - Lifecycle (stop)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConfigurationEngineTest {

    @TempDir
    Path tempDir;

    private ConfigurationEngine engine;

    @BeforeEach
    void setUp() {
        engine = ConfigurationEngine.builder(tempDir)
                .variable("ENV", "test")
                .variable("APP_NAME", "ghatana")
                .build();
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            // synchronous cleanup
            engine.stop();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Loading
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("loadConfig — loads a YAML file and registers it")
    void loadConfig_registersConfig() throws Exception {
        Path yaml = writeYaml(tempDir, "my-config.yaml",
                "name: test-config\nlevel: INFO\n");

        Eventloop eventloop = Eventloop.builder().build();
        eventloop.submit(() ->
                engine.loadConfig("my-config", yaml, ConfigType.SYSTEM)
                        .whenResult(source -> {
                            assertNotNull(source);
                            assertEquals("test-config", source.getString("name").orElse(null));
                            assertEquals("INFO", source.getString("level").orElse(null));
                        })
                        .whenException(e -> fail("Should not fail: " + e)));
        eventloop.run();

        assertTrue(engine.hasConfig("my-config"));
        assertEquals(1, engine.size());
    }

    @Test
    @Order(2)
    @DisplayName("loadConfig — creates a version snapshot")
    void loadConfig_createsVersionSnapshot() throws Exception {
        Path yaml = writeYaml(tempDir, "versioned.yaml",
                "version: 1\nkey: value\n");

        Eventloop eventloop = Eventloop.builder().build();
        eventloop.submit(() ->
                engine.loadConfig("versioned", yaml, ConfigType.SYSTEM)
                        .whenResult(source -> {
                            assertEquals("v1", engine.getCurrentVersion("versioned"));
                            assertEquals(List.of("v1"), engine.listVersions("versioned"));
                        })
                        .whenException(e -> fail("Should not fail: " + e)));
        eventloop.run();
    }

    @Test
    @Order(3)
    @DisplayName("loadConfig — reloading with changed content bumps version")
    void loadConfig_reloadBumpsVersion() throws Exception {
        Path yaml = writeYaml(tempDir, "bumping.yaml", "version: 1\n");

        Eventloop eventloop = Eventloop.builder().build();
        eventloop.submit(() ->
                engine.loadConfig("bumping", yaml, ConfigType.SYSTEM)
                        .then($ -> {
                            try {
                                Files.writeString(yaml, "version: 2\n");
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            return engine.loadConfig("bumping", yaml, ConfigType.SYSTEM);
                        })
                        .whenResult(source -> {
                            assertEquals("v2", engine.getCurrentVersion("bumping"));
                            assertEquals(List.of("v2", "v1"), engine.listVersions("bumping"));
                        })
                        .whenException(e -> fail("Should not fail: " + e)));
        eventloop.run();
    }

    @Test
    @Order(4)
    @DisplayName("loadConfig — unchanged content does not bump version")
    void loadConfig_unchangedContentNoBump() throws Exception {
        Path yaml = writeYaml(tempDir, "stable.yaml", "stable: true\n");

        Eventloop eventloop = Eventloop.builder().build();
        eventloop.submit(() ->
                engine.loadConfig("stable", yaml, ConfigType.SYSTEM)
                        .then($ -> engine.loadConfig("stable", yaml, ConfigType.SYSTEM))
                        .whenResult(source -> {
                            assertEquals("v1", engine.getCurrentVersion("stable"));
                            assertEquals(1, engine.listVersions("stable").size());
                        })
                        .whenException(e -> fail("Should not fail: " + e)));
        eventloop.run();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Directory Loading
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("loadDirectory — loads all YAML files recursively")
    void loadDirectory_loadsAll() throws Exception {
        // Create structure:
        // agents/
        //   fraud-detector.yaml
        // pipelines/
        //   ingestion.yml
        // system.yaml
        Path agentsDir = tempDir.resolve("agents");
        Files.createDirectories(agentsDir);
        writeYaml(agentsDir, "fraud-detector.yaml",
                "type: deterministic\nname: fraud-detector\n");

        Path pipelinesDir = tempDir.resolve("pipelines");
        Files.createDirectories(pipelinesDir);
        writeYaml(pipelinesDir, "ingestion.yml",
                "stages:\n  - name: ingest\n");

        writeYaml(tempDir, "system.yaml",
                "logging:\n  level: DEBUG\n");

        Eventloop eventloop = Eventloop.builder().build();
        eventloop.submit(() ->
                engine.loadDirectory(tempDir)
                        .whenResult($ -> {
                            assertEquals(3, engine.size());
                            assertTrue(engine.hasConfig("agents.fraud-detector"));
                            assertTrue(engine.hasConfig("pipelines.ingestion"));
                            assertTrue(engine.hasConfig("system"));
                        })
                        .whenException(e -> fail("Should not fail: " + e)));
        eventloop.run();
    }

    @Test
    @Order(11)
    @DisplayName("loadDirectory — fails for non-directory path")
    void loadDirectory_failsNonDir() throws Exception {
        Path file = writeYaml(tempDir, "not-a-dir.yaml", "x: 1\n");

        Eventloop eventloop = Eventloop.builder().build();
        eventloop.submit(() ->
                engine.loadDirectory(file)
                        .whenResult($ -> fail("Should fail"))
                        .whenException(e ->
                                assertInstanceOf(ConfigurationException.class, e)));
        eventloop.run();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Interpolation
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(20)
    @DisplayName("interpolate — resolves registered variables")
    void interpolate_resolvesVariables() {
        assertEquals("test", engine.interpolate("${ENV}"));
        assertEquals("ghatana", engine.interpolate("${APP_NAME}"));
        assertEquals("running in test", engine.interpolate("running in ${ENV}"));
    }

    @Test
    @Order(21)
    @DisplayName("interpolate — uses defaults for missing variables")
    void interpolate_usesDefaults() {
        assertEquals("fallback", engine.interpolate("${MISSING:fallback}"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Versioning
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(30)
    @DisplayName("getCurrentVersion — returns v0 for unknown config")
    void getCurrentVersion_unknown() {
        assertEquals("v0", engine.getCurrentVersion("nonexistent"));
    }

    @Test
    @Order(31)
    @DisplayName("listVersions — returns empty for unknown config")
    void listVersions_unknown() {
        assertTrue(engine.listVersions("nonexistent").isEmpty());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Rollback
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(40)
    @DisplayName("rollback — restores to previous version")
    void rollback_restoresVersion() throws Exception {
        Path yaml = writeYaml(tempDir, "rollback-test.yaml", "key: v1\n");

        Eventloop eventloop = Eventloop.builder().build();
        eventloop.submit(() ->
                engine.loadConfig("rollback-test", yaml, ConfigType.SYSTEM)
                        .then($ -> {
                            try {
                                Files.writeString(yaml, "key: v2\n");
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            return engine.loadConfig("rollback-test", yaml, ConfigType.SYSTEM);
                        })
                        .then($ -> {
                            assertEquals("v2", engine.getCurrentVersion("rollback-test"));
                            return engine.rollback("rollback-test", "v1");
                        })
                        .whenResult($ ->
                                // After rollback, current version is v3 (rollback creates new snapshot)
                                assertEquals("v3", engine.getCurrentVersion("rollback-test")))
                        .whenException(e -> fail("Should not fail: " + e)));
        eventloop.run();
    }

    @Test
    @Order(41)
    @DisplayName("rollback — fails for unknown version")
    void rollback_failsUnknownVersion() throws Exception {
        Path yaml = writeYaml(tempDir, "rollback-fail.yaml", "k: v\n");

        Eventloop eventloop = Eventloop.builder().build();
        eventloop.submit(() ->
                engine.loadConfig("rollback-fail", yaml, ConfigType.SYSTEM)
                        .then($ -> engine.rollback("rollback-fail", "v99"))
                        .whenResult($ -> fail("Should fail"))
                        .whenException(e ->
                                assertInstanceOf(ConfigurationException.class, e)));
        eventloop.run();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Change Listeners
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(50)
    @DisplayName("onConfigChange — listener receives rollback events")
    void onConfigChange_rollbackEvent() throws Exception {
        Path yaml = writeYaml(tempDir, "listener-test.yaml", "state: original\n");

        AtomicReference<ConfigChange> received = new AtomicReference<>();
        engine.onConfigChange(received::set);

        Eventloop eventloop = Eventloop.builder().build();
        eventloop.submit(() ->
                engine.loadConfig("listener-test", yaml, ConfigType.SYSTEM)
                        .then($ -> {
                            try {
                                Files.writeString(yaml, "state: modified\n");
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            return engine.loadConfig("listener-test", yaml, ConfigType.SYSTEM);
                        })
                        .then($ -> engine.rollback("listener-test", "v1"))
                        .whenException(e -> fail("Should not fail: " + e)));
        eventloop.run();

        ConfigChange change = received.get();
        assertNotNull(change);
        assertEquals("listener-test", change.configName());
        assertEquals(ConfigChange.ChangeType.ROLLED_BACK, change.changeType());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ConfigType
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(60)
    @DisplayName("ConfigType — fromDirectoryName resolves correctly")
    void configType_fromDirectoryName() {
        assertEquals(ConfigType.AGENT, ConfigType.fromDirectoryName("agents"));
        assertEquals(ConfigType.PIPELINE, ConfigType.fromDirectoryName("pipelines"));
        assertEquals(ConfigType.SYSTEM, ConfigType.fromDirectoryName("system"));
        assertEquals(ConfigType.SYSTEM, ConfigType.fromDirectoryName("unknown"));
        assertEquals(ConfigType.SYSTEM, ConfigType.fromDirectoryName(null));
        assertEquals(ConfigType.SYSTEM, ConfigType.fromDirectoryName(""));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(70)
    @DisplayName("stop — clears registry")
    void stop_clearsAll() throws Exception {
        Path yaml = writeYaml(tempDir, "stop-test.yaml", "x: 1\n");

        Eventloop eventloop = Eventloop.builder().build();
        eventloop.submit(() ->
                engine.loadConfig("stop-test", yaml, ConfigType.SYSTEM)
                        .then($ -> engine.stop())
                        .whenResult($ -> assertEquals(0, engine.size()))
                        .whenException(e -> fail("Should not fail: " + e)));
        eventloop.run();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Registry Access
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(80)
    @DisplayName("getConfig — returns empty for missing config")
    void getConfig_empty() {
        assertTrue(engine.getConfig("missing").isEmpty());
    }

    @Test
    @Order(81)
    @DisplayName("listConfigNames — initially empty")
    void listConfigNames_empty() {
        assertTrue(engine.listConfigNames().isEmpty());
    }

    @Test
    @Order(82)
    @DisplayName("hasConfig — false for unloaded")
    void hasConfig_false() {
        assertFalse(engine.hasConfig("ghost"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ConfigVersionStore
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(90)
    @DisplayName("ConfigVersionStore — snapshot and retrieve content")
    void versionStore_snapshotAndRetrieve() throws Exception {
        Path versionDir = tempDir.resolve("versions");
        ConfigVersionStore store = new ConfigVersionStore(versionDir);

        Path yaml = writeYaml(tempDir, "store-test.yaml", "content: original\n");
        String v1 = store.snapshot("test-cfg", yaml);
        assertEquals("v1", v1);

        // Change content
        Files.writeString(yaml, "content: updated\n");
        String v2 = store.snapshot("test-cfg", yaml);
        assertEquals("v2", v2);

        assertEquals("v2", store.getCurrentVersion("test-cfg"));
        assertEquals(List.of("v2", "v1"), store.listVersions("test-cfg"));
        assertEquals(2, store.totalSnapshots());

        // Read version content
        assertTrue(store.getVersionContent("test-cfg", "v1").isPresent());
        assertTrue(store.getVersionContent("test-cfg", "v1").get().contains("original"));
        assertTrue(store.getVersionContent("test-cfg", "v2").get().contains("updated"));
    }

    @Test
    @Order(91)
    @DisplayName("ConfigVersionStore — rollback returns correct path")
    void versionStore_rollback() throws Exception {
        Path versionDir = tempDir.resolve("versions2");
        ConfigVersionStore store = new ConfigVersionStore(versionDir);

        Path yaml = writeYaml(tempDir, "rb-test.yaml", "v: 1\n");
        store.snapshot("rb", yaml);
        Files.writeString(yaml, "v: 2\n");
        store.snapshot("rb", yaml);

        var restored = store.rollback("rb", "v1");
        assertTrue(restored.isPresent());
        assertEquals("v3", store.getCurrentVersion("rb"));
    }

    @Test
    @Order(92)
    @DisplayName("ConfigVersionStore — skips snapshot for unchanged content")
    void versionStore_skipUnchanged() throws Exception {
        Path versionDir = tempDir.resolve("versions3");
        ConfigVersionStore store = new ConfigVersionStore(versionDir);

        Path yaml = writeYaml(tempDir, "dup-test.yaml", "same: content\n");
        store.snapshot("dup", yaml);
        String v = store.snapshot("dup", yaml); // same content
        assertEquals("v1", v); // no bump
        assertEquals(1, store.totalSnapshots());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ConfigChange
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(100)
    @DisplayName("ConfigChange — record accessors work")
    void configChange_recordAccessors() {
        ConfigChange change = new ConfigChange("test", ConfigChange.ChangeType.MODIFIED, "v2");
        assertEquals("test", change.configName());
        assertEquals(ConfigChange.ChangeType.MODIFIED, change.changeType());
        assertEquals("v2", change.version());
        assertNotNull(change.timestamp());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    private static Path writeYaml(Path dir, String filename, String content)
            throws IOException {
        Path file = dir.resolve(filename);
        Files.writeString(file, content);
        return file;
    }
}
