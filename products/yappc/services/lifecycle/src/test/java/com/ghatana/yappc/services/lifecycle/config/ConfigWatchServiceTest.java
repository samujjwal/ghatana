/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ConfigWatchService}, {@link PolicyReloadListener}, and
 * {@link AgentDefinitionReloadListener}.
 *
 * <p>Covers plan tasks:
 * <ul>
 *   <li>8.3.3 — policy YAML change detected → listener notified within 10 s</li>
 *   <li>8.3.4 — agent definition YAML change detected → listener notified within 10 s</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Unit/integration tests for ConfigWatchService hot reload
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ConfigWatchService Tests (8.3)")
class ConfigWatchServiceTest {

    @TempDir
    Path tempDir;

    private ConfigWatchService watchService;
    private List<ConfigChangeEvent> capturedEvents;

    @BeforeEach
    void createDirectoryStructure() throws IOException {
        // Create config sub-directories
        Files.createDirectories(tempDir.resolve("policies"));
        Files.createDirectories(tempDir.resolve("agents/definitions"));
        Files.createDirectories(tempDir.resolve("workflows"));
        Files.createDirectories(tempDir.resolve("lifecycle"));

        capturedEvents = new CopyOnWriteArrayList<>();
    }

    @AfterEach
    void stopWatcher() {
        if (watchService != null) {
            watchService.close();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8.3.1 — Basic watcher lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("8.3.1.1 — ConfigWatchService starts and stops cleanly")
    void startsAndStops() {
        watchService = new ConfigWatchService(tempDir, List.of());
        watchService.start();
        watchService.close();
        // no exception = pass
    }

    @Test
    @DisplayName("8.3.1.2 — start() is idempotent")
    void startIsIdempotent() {
        watchService = new ConfigWatchService(tempDir, List.of());
        watchService.start();
        watchService.start(); // second call must be ignored
        watchService.close();
    }

    @Test
    @DisplayName("8.3.1.3 — throws for non-existent directory")
    void throwsForNonExistentDir() {
        Path missing = tempDir.resolve("does-not-exist");
        assertThatThrownBy(() -> new ConfigWatchService(missing, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8.3.2 — No-hot-reload enforcement
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("8.3.2 — changes to transitions.yaml and stages.yaml do NOT trigger listeners")
    void noHotReloadFilesNotDeliveredToListeners() throws IOException, InterruptedException {
        List<ConfigChangeEvent> received = new CopyOnWriteArrayList<>();
        ConfigReloadListener catchAll = new ConfigReloadListener() {
            @Override public boolean accepts(String p) { return true; }
            @Override public void onConfigChanged(ConfigChangeEvent e) { received.add(e); }
        };

        watchService = new ConfigWatchService(tempDir, List.of(catchAll));
        watchService.start();

        Files.createDirectories(tempDir.resolve("lifecycle"));
        Files.writeString(tempDir.resolve("lifecycle/transitions.yaml"), "transitions: []");
        Files.writeString(tempDir.resolve("lifecycle/stages.yaml"), "stages: []");

        // Wait briefly to allow the watcher to process events
        // The no-reload guard in ConfigWatchService must suppress these events
        TimeUnit.MILLISECONDS.sleep(500);

        // Wait a bit more to let the watcher loop process
        TimeUnit.SECONDS.sleep(2);

        assertThat(received)
                .as("No events should reach listeners for no-hot-reload files")
                .isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8.3.3 — Policy hot reload
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("8.3.3 — policy YAML change → PolicyReloadListener notified within 10 s")
    void policyFileChangeNotifiesListener() throws IOException, InterruptedException {
        List<ConfigChangeEvent> received = new CopyOnWriteArrayList<>();
        PolicyReloadListener policyListener = new PolicyReloadListener(received::add);

        watchService = new ConfigWatchService(tempDir, List.of(policyListener));
        watchService.start();

        // Write a new policy file
        Path policyFile = tempDir.resolve("policies/security-policy.yaml");
        Files.writeString(policyFile, "name: security\nrules: []");

        // Wait up to 10 seconds for the notification (task 8.3.3 contract)
        long deadline = System.currentTimeMillis() + 10_000;
        while (received.isEmpty() && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(200);
        }

        assertThat(received).as("PolicyReloadListener must be notified within 10 s").isNotEmpty();
        assertThat(received.get(0).relativePath()).isEqualTo("policies/security-policy.yaml");
    }

    @Test
    @DisplayName("8.3.3 — policy file modification triggers reload (not just creation)")
    void policyFileModificationTriggers() throws IOException, InterruptedException {
        List<ConfigChangeEvent> received = new CopyOnWriteArrayList<>();
        PolicyReloadListener policyListener = new PolicyReloadListener(received::add);

        // Write initial file BEFORE starting watcher so creation event is not captured
        Path policyFile = tempDir.resolve("policies/team-policy.yaml");
        Files.writeString(policyFile, "name: team\nrules: []");

        watchService = new ConfigWatchService(tempDir, List.of(policyListener));
        watchService.start();

        // Modify the file
        TimeUnit.MILLISECONDS.sleep(200);
        Files.writeString(policyFile, "name: team\nrules:\n  - id: rule-1");

        long deadline = System.currentTimeMillis() + 10_000;
        while (received.isEmpty() && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(200);
        }

        assertThat(received).as("Policy modification must trigger listener within 10 s").isNotEmpty();
    }

    @Test
    @DisplayName("8.3.3 — non-policy files are NOT delivered to PolicyReloadListener")
    void nonPolicyFilesIgnoredByPolicyListener() throws IOException, InterruptedException {
        List<ConfigChangeEvent> received = new CopyOnWriteArrayList<>();
        PolicyReloadListener policyListener = new PolicyReloadListener(received::add);

        watchService = new ConfigWatchService(tempDir, List.of(policyListener));
        watchService.start();

        // Write an agent file — PolicyReloadListener must NOT accept it
        Files.writeString(tempDir.resolve("agents/definitions/planner.yaml"), "id: planner");

        TimeUnit.SECONDS.sleep(2);

        assertThat(received)
                .as("PolicyReloadListener must not fire for agent files")
                .isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8.3.4 — Agent definition hot reload
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("8.3.4 — agent YAML change → AgentDefinitionReloadListener notified within 10 s")
    void agentFileChangeNotifiesListener() throws IOException, InterruptedException {
        List<ConfigChangeEvent> received = new CopyOnWriteArrayList<>();
        AgentDefinitionReloadListener agentListener = new AgentDefinitionReloadListener(received::add);

        watchService = new ConfigWatchService(tempDir, List.of(agentListener));
        watchService.start();

        Path agentFile = tempDir.resolve("agents/definitions/code-reviewer.yaml");
        Files.writeString(agentFile, "id: code-reviewer\nversion: 1.0.0");

        long deadline = System.currentTimeMillis() + 10_000;
        while (received.isEmpty() && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(200);
        }

        assertThat(received).as("AgentDefinitionReloadListener must be notified within 10 s").isNotEmpty();
        assertThat(received.get(0).relativePath()).contains("code-reviewer.yaml");
    }

    @Test
    @DisplayName("8.3.4 — workflow YAML change → AgentDefinitionReloadListener notified")
    void workflowFileChangeNotifiesListener() throws IOException, InterruptedException {
        List<ConfigChangeEvent> received = new CopyOnWriteArrayList<>();
        AgentDefinitionReloadListener agentListener = new AgentDefinitionReloadListener(received::add);

        watchService = new ConfigWatchService(tempDir, List.of(agentListener));
        watchService.start();

        Path workflowFile = tempDir.resolve("workflows/release-workflow.yaml");
        Files.writeString(workflowFile, "id: release-workflow\nsteps: []");

        long deadline = System.currentTimeMillis() + 10_000;
        while (received.isEmpty() && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(200);
        }

        assertThat(received).as("Workflow file change must trigger AgentDefinitionReloadListener").isNotEmpty();
        assertThat(received.get(0).relativePath()).isEqualTo("workflows/release-workflow.yaml");
    }

    @Test
    @DisplayName("8.3.4 — AgentDefinitionReloadListener accepts() only agent and workflow paths")
    void listenerAcceptsCorrectPaths() {
        AgentDefinitionReloadListener listener = new AgentDefinitionReloadListener(e -> {});

        assertThat(listener.accepts("agents/definitions/planner.yaml")).isTrue();
        assertThat(listener.accepts("agents/registry.yaml")).isTrue();
        assertThat(listener.accepts("workflows/release.yaml")).isTrue();
        assertThat(listener.accepts("policies/security.yaml")).isFalse();
        assertThat(listener.accepts("lifecycle/stages.yaml")).isFalse();
        assertThat(listener.accepts("agents/definitions/planner.json")).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multi-listener routing
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Multiple listeners only receive events they accept")
    void multipleListenersRoutedCorrectly() throws IOException, InterruptedException {
        List<ConfigChangeEvent> policyEvents = new CopyOnWriteArrayList<>();
        List<ConfigChangeEvent> agentEvents  = new CopyOnWriteArrayList<>();

        PolicyReloadListener          policyListener = new PolicyReloadListener(policyEvents::add);
        AgentDefinitionReloadListener agentListener  = new AgentDefinitionReloadListener(agentEvents::add);

        watchService = new ConfigWatchService(tempDir, List.of(policyListener, agentListener));
        watchService.start();

        Files.writeString(tempDir.resolve("policies/rbac.yaml"), "name: rbac");
        Files.writeString(tempDir.resolve("agents/definitions/tester.yaml"), "id: tester");

        long deadline = System.currentTimeMillis() + 10_000;
        while ((policyEvents.isEmpty() || agentEvents.isEmpty()) && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(200);
        }

        assertThat(policyEvents).as("Policy listener must receive the policy event").isNotEmpty();
        assertThat(agentEvents).as("Agent listener must receive the agent event").isNotEmpty();

        // Cross-contamination check
        boolean policyReceivedAgentEvent = policyEvents.stream()
                .anyMatch(e -> e.relativePath().startsWith("agents/"));
        boolean agentReceivedPolicyEvent = agentEvents.stream()
                .anyMatch(e -> e.relativePath().startsWith("policies/"));

        assertThat(policyReceivedAgentEvent).as("Policy listener must not receive agent events").isFalse();
        assertThat(agentReceivedPolicyEvent).as("Agent listener must not receive policy events").isFalse();
    }
}
