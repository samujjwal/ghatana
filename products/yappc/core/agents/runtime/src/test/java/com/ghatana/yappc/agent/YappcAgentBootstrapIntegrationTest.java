package com.ghatana.yappc.agent;

import com.ghatana.agent.framework.planner.PlannerAgentFactory;
import com.ghatana.yappc.agent.tools.YappcToolRegistry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for YappcAgentBootstrap using AEP's PlannerAgentFactory.
 *
 * @doc.type test
 * @doc.purpose Verify YAPPC agents integrate correctly with AEP runtime
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@DisplayName("YAPPC Agent Bootstrap Integration Tests [GH-90000]")
class YappcAgentBootstrapIntegrationTest extends EventloopTestBase {

    @TempDir
    Path tempConfigDir;

    private Eventloop eventloop;

    @BeforeEach
    void setUp() { // GH-90000
        eventloop = eventloop(); // GH-90000
    }

    @Test
    @DisplayName("Should create bootstrap instance with default config path [GH-90000]")
    void shouldCreateBootstrapWithDefaultPath() { // GH-90000
        // WHEN
        YappcAgentBootstrap bootstrap = YappcAgentBootstrap.create(eventloop); // GH-90000

        // THEN
        assertThat(bootstrap).isNotNull(); // GH-90000
        assertThat(bootstrap.getFactory()).isNotNull(); // GH-90000
        assertThat(bootstrap.getRegistry()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should create bootstrap instance with custom config path [GH-90000]")
    void shouldCreateBootstrapWithCustomPath() { // GH-90000
        // WHEN
        YappcAgentBootstrap bootstrap = YappcAgentBootstrap.create( // GH-90000
            eventloop,
            tempConfigDir.toString() // GH-90000
        );

        // THEN
        assertThat(bootstrap).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should throw exception when eventloop is null [GH-90000]")
    void shouldThrowExceptionWhenEventloopIsNull() { // GH-90000
        // WHEN/THEN
        assertThatThrownBy(() -> YappcAgentBootstrap.create(null)) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("Eventloop cannot be null [GH-90000]");
    }

    @Test
    @DisplayName("Should throw exception when config path is null or empty [GH-90000]")
    void shouldThrowExceptionWhenConfigPathIsInvalid() { // GH-90000
        // WHEN/THEN
        assertThatThrownBy(() -> YappcAgentBootstrap.create(eventloop, null)) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("Config base path cannot be null or empty [GH-90000]");

        assertThatThrownBy(() -> YappcAgentBootstrap.create(eventloop, "")) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("Config base path cannot be null or empty [GH-90000]");
    }

    @Test
    @DisplayName("Should register all YAPPC tools successfully [GH-90000]")
    void shouldRegisterAllTools() { // GH-90000
        // GIVEN
        PlannerAgentFactory factory = new PlannerAgentFactory(); // GH-90000

        // WHEN
        YappcToolRegistry.registerAll(factory); // GH-90000

        // THEN
        // No exception thrown means success
        assertThat(factory).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should succeed initialization when valid agent YAML definitions exist [GH-90000]")
    void shouldFailInitializationWhenAgentCreationNotImplemented() throws IOException { // GH-90000
        // GIVEN - valid agent YAML exists; bootstrap now parses YAML into raw maps
        createMockAgentDefinitions(); // GH-90000

        YappcAgentBootstrap bootstrap = YappcAgentBootstrap.create( // GH-90000
            eventloop,
            tempConfigDir.toString() // GH-90000
        );

        // WHEN - initialization should succeed (agents loaded as raw YAML maps) // GH-90000
        runPromise(() -> bootstrap.initialize()); // GH-90000

        // THEN - agent is accessible from registry
        assertThat(bootstrap.getAgent("test-agent [GH-90000]")).isNotNull();
    }

    @Test
    @DisplayName("Should succeed agent lookup after initialization with valid YAML [GH-90000]")
    void shouldFailAgentLookupWhenCreationNotImplemented() throws IOException { // GH-90000
        // GIVEN - valid agent YAML exists; bootstrap loads it as a raw map
        createMockAgentDefinitions(); // GH-90000

        YappcAgentBootstrap bootstrap = YappcAgentBootstrap.create( // GH-90000
            eventloop,
            tempConfigDir.toString() // GH-90000
        );

        // WHEN
        runPromise(() -> bootstrap.initialize()); // GH-90000

        // THEN - loaded definition is accessible
        Object agentDef = bootstrap.getAgent("test-agent [GH-90000]");
        assertThat(agentDef).isNotNull(); // GH-90000
        assertThat(agentDef).isInstanceOf(java.util.Map.class); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        java.util.Map<String, Object> defMap = (java.util.Map<String, Object>) agentDef; // GH-90000
        assertThat(defMap.get("id [GH-90000]")).isEqualTo("test-agent [GH-90000]");
    }

    @Test
    @DisplayName("Should throw exception when accessing agents before initialization [GH-90000]")
    void shouldThrowExceptionWhenNotInitialized() { // GH-90000
        // GIVEN
        YappcAgentBootstrap bootstrap = YappcAgentBootstrap.create( // GH-90000
            eventloop,
            tempConfigDir.toString() // GH-90000
        );

        // WHEN/THEN
        assertThatThrownBy(() -> bootstrap.getAgent("any-agent [GH-90000]"))
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("Bootstrap not initialized [GH-90000]");
    }

    @Test
    @DisplayName("Should handle repeated initialization attempts gracefully (idempotent) [GH-90000]")
    void shouldHandleMultipleInitializations() throws IOException { // GH-90000
        // GIVEN - valid agent YAML exists
        createMockAgentDefinitions(); // GH-90000

        YappcAgentBootstrap bootstrap = YappcAgentBootstrap.create( // GH-90000
            eventloop,
            tempConfigDir.toString() // GH-90000
        );

        // WHEN - first initialization succeeds
        runPromise(() -> bootstrap.initialize()); // GH-90000
        assertThat(bootstrap.getAgent("test-agent [GH-90000]")).isNotNull();

        // THEN - second initialization also succeeds (idempotent — already initialized) // GH-90000
        runPromise(() -> bootstrap.initialize()); // GH-90000
        assertThat(bootstrap.getAgent("test-agent [GH-90000]")).isNotNull();
    }

    // ==================== HELPER METHODS ====================

    private void createMockAgentDefinitions() throws IOException { // GH-90000
        Path definitionsDir = tempConfigDir.resolve("definitions [GH-90000]");
        Files.createDirectories(definitionsDir); // GH-90000

        // Create a simple mock agent definition
        String mockAgentYaml = """
            id: "test-agent"
            name: "Test Agent"
            version: "1.0.0"
            description: "Test agent for integration tests"

            metadata:
              level: 3
              domain: "test"
              tags: ["test"]

            generator:
              type: "template"
              engine: "liquid"
              template_path: "test.liquid"

            memory:
              episodic:
                enabled: false
              semantic:
                enabled: false
              procedural:
                enabled: false

            tools: []

            capabilities:
              - "test-capability"

            routing:
              input_types: ["TestInput"]
              output_types: ["TestOutput"]

            delegation:
              can_delegate_to: []
              escalates_to: "none"

            performance:
              expected_latency_ms: 100
              max_latency_ms: 500
              timeout_ms: 1000
            """;

        Files.writeString( // GH-90000
            definitionsDir.resolve("test-agent.yaml [GH-90000]"),
            mockAgentYaml
        );
    }
}
