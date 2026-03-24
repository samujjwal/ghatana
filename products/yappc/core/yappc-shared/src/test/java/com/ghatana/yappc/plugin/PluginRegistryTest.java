package com.ghatana.yappc.plugin;

import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PluginRegistry.
 
 * @doc.type class
 * @doc.purpose Handles plugin registry test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class PluginRegistryTest extends EventloopTestBase {
    
    private PluginRegistry registry;
    private PluginContext context;
    
    @BeforeEach
    void setUp() {
        context = DefaultPluginContext.builder()
            .yappcVersion("1.0.0")
            .pluginDirectory("./test-plugins")
            .build();
        
        registry = PluginRegistry.create(context);
    }
    
    @Test
    void testInitializeAndShutdown() throws Exception {
        runPromise(() -> registry.initialize());
        assertTrue(registry.getPluginCount() >= 0);
        
        runPromise(() -> registry.shutdown());
        assertEquals(0, registry.getPluginCount());
    }
    
    @Test
    void testRegisterPlugin() throws Exception {
        runPromise(() -> registry.initialize());
        
        TestValidatorPlugin plugin = new TestValidatorPlugin();
        runPromise(() -> registry.registerPlugin(plugin));
        
        assertTrue(registry.isRegistered("test-validator"));
        assertEquals(1, registry.getPluginCount());
    }
    
    @Test
    void testGetPlugin() throws Exception {
        runPromise(() -> registry.initialize());
        
        TestValidatorPlugin plugin = new TestValidatorPlugin();
        runPromise(() -> registry.registerPlugin(plugin));
        
        var retrieved = registry.getPlugin("test-validator");
        assertTrue(retrieved.isPresent());
        assertEquals("test-validator", retrieved.get().getMetadata().getId());
    }
    
    @Test
    void testGetPluginsByType() throws Exception {
        runPromise(() -> registry.initialize());
        
        TestValidatorPlugin validator = new TestValidatorPlugin();
        TestGeneratorPlugin generator = new TestGeneratorPlugin();
        
        runPromise(() -> registry.registerPlugin(validator));
        runPromise(() -> registry.registerPlugin(generator));
        
        List<ValidatorPlugin> validators = registry.getValidators();
        assertEquals(1, validators.size());
        
        List<GeneratorPlugin> generators = registry.getGenerators();
        assertEquals(1, generators.size());
    }
    
    @Test
    void testGetValidatorsByCategory() throws Exception {
        runPromise(() -> registry.initialize());
        
        TestValidatorPlugin plugin = new TestValidatorPlugin();
        runPromise(() -> registry.registerPlugin(plugin));
        
        List<ValidatorPlugin> validators = registry.getValidatorsByCategory("test");
        assertEquals(1, validators.size());
    }
    
    @Test
    void testGetGeneratorsByLanguage() throws Exception {
        runPromise(() -> registry.initialize());
        
        TestGeneratorPlugin plugin = new TestGeneratorPlugin();
        runPromise(() -> registry.registerPlugin(plugin));
        
        List<GeneratorPlugin> generators = registry.getGeneratorsByLanguage("java");
        assertEquals(1, generators.size());
    }
    
    @Test
    void testGetAgentsByPhase() throws Exception {
        runPromise(() -> registry.initialize());
        
        TestAgentPlugin plugin = new TestAgentPlugin();
        runPromise(() -> registry.registerPlugin(plugin));
        
        List<AgentPlugin> agents = registry.getAgentsByPhase("planning");
        assertEquals(1, agents.size());
    }
    
    @Test
    void testGetAgent() throws Exception {
        runPromise(() -> registry.initialize());
        
        TestAgentPlugin plugin = new TestAgentPlugin();
        runPromise(() -> registry.registerPlugin(plugin));
        
        var agent = registry.getAgent("planning", "create-architecture");
        assertTrue(agent.isPresent());
    }
    
    @Test
    void testCheckHealth() throws Exception {
        runPromise(() -> registry.initialize());
        
        TestValidatorPlugin plugin = new TestValidatorPlugin();
        runPromise(() -> registry.registerPlugin(plugin));
        
        Map<String, HealthStatus> healthMap = runPromise(() -> registry.checkHealth());
        
        assertNotNull(healthMap);
        assertTrue(healthMap.containsKey("test-validator"));
        assertTrue(healthMap.get("test-validator").isHealthy());
    }
    
    // Test plugin implementations
    
    static class TestValidatorPlugin implements ValidatorPlugin {
        
        @Override
        public Promise<Void> initialize(PluginContext context) {
            return Promise.complete();
        }
        
        @Override
        public Promise<Void> start() {
            return Promise.complete();
        }
        
        @Override
        public Promise<Void> stop() {
            return Promise.complete();
        }
        
        @Override
        public Promise<Void> shutdown() {
            return Promise.complete();
        }
        
        @Override
        public PluginMetadata getMetadata() {
            return PluginMetadata.builder()
                .id("test-validator")
                .name("Test Validator")
                .version("1.0.0")
                .build();
        }
        
        @Override
        public PluginCapabilities getCapabilities() {
            return PluginCapabilities.builder().build();
        }
        
        @Override
        public Promise<HealthStatus> checkHealth() {
            return Promise.of(HealthStatus.healthy());
        }
        
        @Override
        public Promise<ValidationResult> validate(ValidationContext context) {
            return Promise.of(ValidationResult.builder()
                .validatorId("test-validator")
                .valid(true)
                .build());
        }
        
        @Override
        public String getValidatorCategory() {
            return "test";
        }
    }
    
    static class TestGeneratorPlugin implements GeneratorPlugin {
        
        @Override
        public Promise<Void> initialize(PluginContext context) {
            return Promise.complete();
        }
        
        @Override
        public Promise<Void> start() {
            return Promise.complete();
        }
        
        @Override
        public Promise<Void> stop() {
            return Promise.complete();
        }
        
        @Override
        public Promise<Void> shutdown() {
            return Promise.complete();
        }
        
        @Override
        public PluginMetadata getMetadata() {
            return PluginMetadata.builder()
                .id("test-generator")
                .name("Test Generator")
                .version("1.0.0")
                .build();
        }
        
        @Override
        public PluginCapabilities getCapabilities() {
            return PluginCapabilities.builder().build();
        }
        
        @Override
        public Promise<HealthStatus> checkHealth() {
            return Promise.of(HealthStatus.healthy());
        }
        
        @Override
        public Promise<GenerationResult> generate(GenerationContext context) {
            return Promise.of(GenerationResult.builder()
                .generatorId("test-generator")
                .success(true)
                .build());
        }
        
        @Override
        public Set<String> getSupportedLanguages() {
            return Set.of("java", "typescript");
        }
    }
    
    static class TestAgentPlugin implements AgentPlugin {
        
        @Override
        public Promise<Void> initialize(PluginContext context) {
            return Promise.complete();
        }
        
        @Override
        public Promise<Void> start() {
            return Promise.complete();
        }
        
        @Override
        public Promise<Void> stop() {
            return Promise.complete();
        }
        
        @Override
        public Promise<Void> shutdown() {
            return Promise.complete();
        }
        
        @Override
        public PluginMetadata getMetadata() {
            return PluginMetadata.builder()
                .id("test-agent")
                .name("Test Agent")
                .version("1.0.0")
                .build();
        }
        
        @Override
        public PluginCapabilities getCapabilities() {
            return PluginCapabilities.builder().build();
        }
        
        @Override
        public Promise<HealthStatus> checkHealth() {
            return Promise.of(HealthStatus.healthy());
        }
        
        @Override
        public <I, O> Promise<StepResult<O>> execute(I input, StepContext context) {
            return Promise.of(StepResult.<O>builder()
                .stepName("create-architecture")
                .success(true)
                .build());
        }
        
        @Override
        public String getSdlcPhase() {
            return "planning";
        }
        
        @Override
        public String getStepName() {
            return "create-architecture";
        }
    }
}
