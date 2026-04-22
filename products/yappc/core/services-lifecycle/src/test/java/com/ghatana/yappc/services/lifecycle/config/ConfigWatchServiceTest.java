/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
@DisplayName("ConfigWatchService Tests (8.3) [GH-90000]")
class ConfigWatchServiceTest {

    @TempDir
    Path tempDir;

    private ConfigWatchService watchService;
    private List<ConfigChangeEvent> capturedEvents;

    @BeforeEach
    void createDirectoryStructure() throws IOException { // GH-90000
        // Create config sub-directories
        Files.createDirectories(tempDir.resolve("policies [GH-90000]"));
        Files.createDirectories(tempDir.resolve("agents/definitions [GH-90000]"));
        Files.createDirectories(tempDir.resolve("workflows [GH-90000]"));
        Files.createDirectories(tempDir.resolve("lifecycle [GH-90000]"));

        capturedEvents = new CopyOnWriteArrayList<>(); // GH-90000
    }

    @AfterEach
    void stopWatcher() { // GH-90000
        if (watchService != null) { // GH-90000
            watchService.close(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8.3.1 — Basic watcher lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("8.3.1.1 — ConfigWatchService starts and stops cleanly [GH-90000]")
    void startsAndStops() { // GH-90000
        watchService = new ConfigWatchService(tempDir, List.of()); // GH-90000
        watchService.start(); // GH-90000
        watchService.close(); // GH-90000
        // no exception = pass
    }

    @Test
    @DisplayName("8.3.1.2 — start() is idempotent [GH-90000]")
    void startIsIdempotent() { // GH-90000
        watchService = new ConfigWatchService(tempDir, List.of()); // GH-90000
        watchService.start(); // GH-90000
        watchService.start(); // second call must be ignored // GH-90000
        watchService.close(); // GH-90000
    }

    @Test
    @DisplayName("8.3.1.3 — throws for non-existent directory [GH-90000]")
    void throwsForNonExistentDir() { // GH-90000
        Path missing = tempDir.resolve("does-not-exist [GH-90000]");
        assertThatThrownBy(() -> new ConfigWatchService(missing, List.of())) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("does not exist [GH-90000]");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8.3.2 — No-hot-reload enforcement
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("8.3.2 — changes to transitions.yaml and stages.yaml do NOT trigger listeners [GH-90000]")
    void noHotReloadFilesNotDeliveredToListeners() throws IOException, InterruptedException { // GH-90000
        List<ConfigChangeEvent> received = new CopyOnWriteArrayList<>(); // GH-90000
        ConfigReloadListener catchAll = new ConfigReloadListener() { // GH-90000
            @Override public boolean accepts(String p) { return true; } // GH-90000
            @Override public void onConfigChanged(ConfigChangeEvent e) { received.add(e); } // GH-90000
        };

        watchService = new ConfigWatchService(tempDir, List.of(catchAll)); // GH-90000
        watchService.start(); // GH-90000

        Files.createDirectories(tempDir.resolve("lifecycle [GH-90000]"));
        Files.writeString(tempDir.resolve("lifecycle/transitions.yaml [GH-90000]"), "transitions: []");
        Files.writeString(tempDir.resolve("lifecycle/stages.yaml [GH-90000]"), "stages: []");

        // Wait briefly to allow the watcher to process events
        // The no-reload guard in ConfigWatchService must suppress these events
        TimeUnit.MILLISECONDS.sleep(500); // GH-90000

        // Wait a bit more to let the watcher loop process
        TimeUnit.SECONDS.sleep(2); // GH-90000

        assertThat(received) // GH-90000
                .as("No events should reach listeners for no-hot-reload files [GH-90000]")
                .isEmpty(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8.3.3 — Policy hot reload
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("8.3.3 — policy YAML change → PolicyReloadListener notified within 10 s [GH-90000]")
    void policyFileChangeNotifiesListener() throws IOException, InterruptedException { // GH-90000
        List<ConfigChangeEvent> received = new CopyOnWriteArrayList<>(); // GH-90000
        PolicyReloadListener policyListener = new PolicyReloadListener(received::add); // GH-90000

        watchService = new ConfigWatchService(tempDir, List.of(policyListener)); // GH-90000
        watchService.start(); // GH-90000

        // Write a new policy file
        Path policyFile = tempDir.resolve("policies/security-policy.yaml [GH-90000]");
        Files.writeString(policyFile, "name: security\nrules: []"); // GH-90000

        // Wait up to 10 seconds for the notification (task 8.3.3 contract) // GH-90000
        long deadline = System.currentTimeMillis() + 10_000; // GH-90000
        while (received.isEmpty() && System.currentTimeMillis() < deadline) { // GH-90000
            TimeUnit.MILLISECONDS.sleep(200); // GH-90000
        }

        assertThat(received).as("PolicyReloadListener must be notified within 10 s [GH-90000]").isNotEmpty();
        assertThat(received.get(0).relativePath()).isEqualTo("policies/security-policy.yaml [GH-90000]");
    }

    @Test
    @DisplayName("8.3.3 — policy file modification triggers reload (not just creation) [GH-90000]")
    void policyFileModificationTriggers() throws IOException, InterruptedException { // GH-90000
        List<ConfigChangeEvent> received = new CopyOnWriteArrayList<>(); // GH-90000
        PolicyReloadListener policyListener = new PolicyReloadListener(received::add); // GH-90000

        // Write initial file BEFORE starting watcher so creation event is not captured
        Path policyFile = tempDir.resolve("policies/team-policy.yaml [GH-90000]");
        Files.writeString(policyFile, "name: team\nrules: []"); // GH-90000

        watchService = new ConfigWatchService(tempDir, List.of(policyListener)); // GH-90000
        watchService.start(); // GH-90000

        // Modify the file
        TimeUnit.MILLISECONDS.sleep(200); // GH-90000
        Files.writeString(policyFile, "name: team\nrules:\n  - id: rule-1"); // GH-90000

        long deadline = System.currentTimeMillis() + 10_000; // GH-90000
        while (received.isEmpty() && System.currentTimeMillis() < deadline) { // GH-90000
            TimeUnit.MILLISECONDS.sleep(200); // GH-90000
        }

        assertThat(received).as("Policy modification must trigger listener within 10 s [GH-90000]").isNotEmpty();
    }

    @Test
    @DisplayName("8.3.3 — non-policy files are NOT delivered to PolicyReloadListener [GH-90000]")
    void nonPolicyFilesIgnoredByPolicyListener() throws IOException, InterruptedException { // GH-90000
        List<ConfigChangeEvent> received = new CopyOnWriteArrayList<>(); // GH-90000
        PolicyReloadListener policyListener = new PolicyReloadListener(received::add); // GH-90000

        watchService = new ConfigWatchService(tempDir, List.of(policyListener)); // GH-90000
        watchService.start(); // GH-90000

        // Write an agent file — PolicyReloadListener must NOT accept it
        Files.writeString(tempDir.resolve("agents/definitions/planner.yaml [GH-90000]"), "id: planner");

        TimeUnit.SECONDS.sleep(2); // GH-90000

        assertThat(received) // GH-90000
                .as("PolicyReloadListener must not fire for agent files [GH-90000]")
                .isEmpty(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8.3.4 — Agent definition hot reload
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("8.3.4 — agent YAML change → AgentDefinitionReloadListener notified within 10 s [GH-90000]")
    void agentFileChangeNotifiesListener() throws IOException, InterruptedException { // GH-90000
        List<ConfigChangeEvent> received = new CopyOnWriteArrayList<>(); // GH-90000
        AgentDefinitionReloadListener agentListener = new AgentDefinitionReloadListener(received::add); // GH-90000

        watchService = new ConfigWatchService(tempDir, List.of(agentListener)); // GH-90000
        watchService.start(); // GH-90000

        Path agentFile = tempDir.resolve("agents/definitions/code-reviewer.yaml [GH-90000]");
        Files.writeString(agentFile, "id: code-reviewer\nversion: 1.0.0"); // GH-90000

        long deadline = System.currentTimeMillis() + 10_000; // GH-90000
        while (received.isEmpty() && System.currentTimeMillis() < deadline) { // GH-90000
            TimeUnit.MILLISECONDS.sleep(200); // GH-90000
        }

        assertThat(received).as("AgentDefinitionReloadListener must be notified within 10 s [GH-90000]").isNotEmpty();
        assertThat(received.get(0).relativePath()).contains("code-reviewer.yaml [GH-90000]");
    }

    @Test
    @DisplayName("8.3.4 — workflow YAML change → AgentDefinitionReloadListener notified [GH-90000]")
    void workflowFileChangeNotifiesListener() throws IOException, InterruptedException { // GH-90000
        List<ConfigChangeEvent> received = new CopyOnWriteArrayList<>(); // GH-90000
        AgentDefinitionReloadListener agentListener = new AgentDefinitionReloadListener(received::add); // GH-90000

        watchService = new ConfigWatchService(tempDir, List.of(agentListener)); // GH-90000
        watchService.start(); // GH-90000

        Path workflowFile = tempDir.resolve("workflows/release-workflow.yaml [GH-90000]");
        Files.writeString(workflowFile, "id: release-workflow\nsteps: []"); // GH-90000

        long deadline = System.currentTimeMillis() + 10_000; // GH-90000
        while (received.isEmpty() && System.currentTimeMillis() < deadline) { // GH-90000
            TimeUnit.MILLISECONDS.sleep(200); // GH-90000
        }

        assertThat(received).as("Workflow file change must trigger AgentDefinitionReloadListener [GH-90000]").isNotEmpty();
        assertThat(received.get(0).relativePath()).isEqualTo("workflows/release-workflow.yaml [GH-90000]");
    }

    @Test
    @DisplayName("8.3.4 — AgentDefinitionReloadListener accepts() only agent and workflow paths [GH-90000]")
    void listenerAcceptsCorrectPaths() { // GH-90000
        AgentDefinitionReloadListener listener = new AgentDefinitionReloadListener(e -> {}); // GH-90000

        assertThat(listener.accepts("agents/definitions/planner.yaml [GH-90000]")).isTrue();
        assertThat(listener.accepts("agents/registry.yaml [GH-90000]")).isTrue();
        assertThat(listener.accepts("workflows/release.yaml [GH-90000]")).isTrue();
        assertThat(listener.accepts("policies/security.yaml [GH-90000]")).isFalse();
        assertThat(listener.accepts("lifecycle/stages.yaml [GH-90000]")).isFalse();
        assertThat(listener.accepts("agents/definitions/planner.json [GH-90000]")).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multi-listener routing
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Multiple listeners only receive events they accept [GH-90000]")
    void multipleListenersRoutedCorrectly() throws IOException, InterruptedException { // GH-90000
        List<ConfigChangeEvent> policyEvents = new CopyOnWriteArrayList<>(); // GH-90000
        List<ConfigChangeEvent> agentEvents  = new CopyOnWriteArrayList<>(); // GH-90000

        PolicyReloadListener          policyListener = new PolicyReloadListener(policyEvents::add); // GH-90000
        AgentDefinitionReloadListener agentListener  = new AgentDefinitionReloadListener(agentEvents::add); // GH-90000

        watchService = new ConfigWatchService(tempDir, List.of(policyListener, agentListener)); // GH-90000
        watchService.start(); // GH-90000

        Files.writeString(tempDir.resolve("policies/rbac.yaml [GH-90000]"), "name: rbac");
        Files.writeString(tempDir.resolve("agents/definitions/tester.yaml [GH-90000]"), "id: tester");

        long deadline = System.currentTimeMillis() + 10_000; // GH-90000
        while ((policyEvents.isEmpty() || agentEvents.isEmpty()) && System.currentTimeMillis() < deadline) { // GH-90000
            TimeUnit.MILLISECONDS.sleep(200); // GH-90000
        }

        assertThat(policyEvents).as("Policy listener must receive the policy event [GH-90000]").isNotEmpty();
        assertThat(agentEvents).as("Agent listener must receive the agent event [GH-90000]").isNotEmpty();

        // Cross-contamination check
        boolean policyReceivedAgentEvent = policyEvents.stream() // GH-90000
                .anyMatch(e -> e.relativePath().startsWith("agents/ [GH-90000]"));
        boolean agentReceivedPolicyEvent = agentEvents.stream() // GH-90000
                .anyMatch(e -> e.relativePath().startsWith("policies/ [GH-90000]"));

        assertThat(policyReceivedAgentEvent).as("Policy listener must not receive agent events [GH-90000]").isFalse();
        assertThat(agentReceivedPolicyEvent).as("Agent listener must not receive policy events [GH-90000]").isFalse();
    }
}
