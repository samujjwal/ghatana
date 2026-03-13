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
@DisplayName("YAPPC Agent Bootstrap Integration Tests")
class YappcAgentBootstrapIntegrationTest extends EventloopTestBase {
    
    @TempDir
    Path tempConfigDir;
    
    private Eventloop eventloop;
    
    @BeforeEach
    void setUp() {
        eventloop = eventloop();
    }
    
    @Test
    @DisplayName("Should create bootstrap instance with default config path")
    void shouldCreateBootstrapWithDefaultPath() {
        // WHEN
        YappcAgentBootstrap bootstrap = YappcAgentBootstrap.create(eventloop);
        
        // THEN
        assertThat(bootstrap).isNotNull();
        assertThat(bootstrap.getFactory()).isNotNull();
        assertThat(bootstrap.getRegistry()).isNotNull();
    }
    
    @Test
    @DisplayName("Should create bootstrap instance with custom config path")
    void shouldCreateBootstrapWithCustomPath() {
        // WHEN
        YappcAgentBootstrap bootstrap = YappcAgentBootstrap.create(
            eventloop, 
            tempConfigDir.toString()
        );
        
        // THEN
        assertThat(bootstrap).isNotNull();
    }
    
    @Test
    @DisplayName("Should throw exception when eventloop is null")
    void shouldThrowExceptionWhenEventloopIsNull() {
        // WHEN/THEN
        assertThatThrownBy(() -> YappcAgentBootstrap.create(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Eventloop cannot be null");
    }
    
    @Test
    @DisplayName("Should throw exception when config path is null or empty")
    void shouldThrowExceptionWhenConfigPathIsInvalid() {
        // WHEN/THEN
        assertThatThrownBy(() -> YappcAgentBootstrap.create(eventloop, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Config base path cannot be null or empty");
        
        assertThatThrownBy(() -> YappcAgentBootstrap.create(eventloop, ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Config base path cannot be null or empty");
    }
    
    @Test
    @DisplayName("Should register all YAPPC tools successfully")
    void shouldRegisterAllTools() {
        // GIVEN
        PlannerAgentFactory factory = new PlannerAgentFactory();
        
        // WHEN
        YappcToolRegistry.registerAll(factory);
        
        // THEN
        // No exception thrown means success
        assertThat(factory).isNotNull();
    }
    
    @Test
    @DisplayName("Should succeed initialization when valid agent YAML definitions exist")
    void shouldFailInitializationWhenAgentCreationNotImplemented() throws IOException {
        // GIVEN - valid agent YAML exists; bootstrap now parses YAML into raw maps
        createMockAgentDefinitions();

        YappcAgentBootstrap bootstrap = YappcAgentBootstrap.create(
            eventloop,
            tempConfigDir.toString()
        );

        // WHEN - initialization should succeed (agents loaded as raw YAML maps)
        runPromise(() -> bootstrap.initialize());

        // THEN - agent is accessible from registry
        assertThat(bootstrap.getAgent("test-agent")).isNotNull();
    }
    
    @Test
    @DisplayName("Should succeed agent lookup after initialization with valid YAML")
    void shouldFailAgentLookupWhenCreationNotImplemented() throws IOException {
        // GIVEN - valid agent YAML exists; bootstrap loads it as a raw map
        createMockAgentDefinitions();

        YappcAgentBootstrap bootstrap = YappcAgentBootstrap.create(
            eventloop,
            tempConfigDir.toString()
        );

        // WHEN
        runPromise(() -> bootstrap.initialize());

        // THEN - loaded definition is accessible
        Object agentDef = bootstrap.getAgent("test-agent");
        assertThat(agentDef).isNotNull();
        assertThat(agentDef).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> defMap = (java.util.Map<String, Object>) agentDef;
        assertThat(defMap.get("id")).isEqualTo("test-agent");
    }
    
    @Test
    @DisplayName("Should throw exception when accessing agents before initialization")
    void shouldThrowExceptionWhenNotInitialized() {
        // GIVEN
        YappcAgentBootstrap bootstrap = YappcAgentBootstrap.create(
            eventloop,
            tempConfigDir.toString()
        );
        
        // WHEN/THEN
        assertThatThrownBy(() -> bootstrap.getAgent("any-agent"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Bootstrap not initialized");
    }
    
    @Test
    @DisplayName("Should handle repeated initialization attempts gracefully (idempotent)")
    void shouldHandleMultipleInitializations() throws IOException {
        // GIVEN - valid agent YAML exists
        createMockAgentDefinitions();

        YappcAgentBootstrap bootstrap = YappcAgentBootstrap.create(
            eventloop,
            tempConfigDir.toString()
        );

        // WHEN - first initialization succeeds
        runPromise(() -> bootstrap.initialize());
        assertThat(bootstrap.getAgent("test-agent")).isNotNull();

        // THEN - second initialization also succeeds (idempotent — already initialized)
        runPromise(() -> bootstrap.initialize());
        assertThat(bootstrap.getAgent("test-agent")).isNotNull();
    }
    
    // ==================== HELPER METHODS ====================
    
    private void createMockAgentDefinitions() throws IOException {
        Path definitionsDir = tempConfigDir.resolve("definitions");
        Files.createDirectories(definitionsDir);
        
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
        
        Files.writeString(
            definitionsDir.resolve("test-agent.yaml"),
            mockAgentYaml
        );
    }
}
