package com.ghatana.virtualorg.framework.agent;

import com.ghatana.virtualorg.framework.config.VirtualOrgAgentConfig;
import com.ghatana.virtualorg.framework.config.ConfigLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the AgentRegistry and factory-based agent creation.
 */
@DisplayName("Agent Registry Tests")
class AgentRegistryTest {

    private AgentRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
    }

    // Helper to create minimal agent configs using ConfigLoader
    private VirtualOrgAgentConfig createConfig(String name) throws Exception {
        String yaml = """
            apiVersion: virtualorg.ghatana.com/v1
            kind: Agent
            metadata:
              name: %s
              namespace: default
            spec:
              template: test
              displayName: Test Agent
            """.formatted(name);
        return ConfigLoader.loadConfig(yaml, VirtualOrgAgentConfig.class);
    }

    @Test
    @DisplayName("Should register and use custom agent factory")
    void shouldRegisterAndUseCustomFactory() throws Exception {
        registry.register(new TestAgentFactory());

        VirtualOrgAgentConfig config = createConfig("test-agent");
        Agent agent = registry.create("test-template", config);

        assertThat(agent).isNotNull();
        assertThat(agent.getId()).isEqualTo("test-agent");
    }

    @Test
    @DisplayName("Should throw exception for unknown template")
    void shouldThrowForUnknownTemplate() throws Exception {
        registry.register(new TestAgentFactory());

        VirtualOrgAgentConfig config = createConfig("agent");

        assertThatThrownBy(() -> registry.create("unknown-template", config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No registered factory");
    }

    @Test
    @DisplayName("Should fall back to default for unknown template with createOrDefault")
    void shouldFallBackToDefaultFactory() throws Exception {
        VirtualOrgAgentConfig config = createConfig("unknown-agent");

        Agent agent = registry.createOrDefault("unknown-template", config);

        assertThat(agent).isNotNull();
        // Default factory creates a generic agent
        assertThat(agent.getId()).isEqualTo("unknown-agent");
    }

    @Test
    @DisplayName("Should select highest priority factory")
    void shouldSelectHighestPriorityFactory() throws Exception {
        registry.register(new LowPriorityFactory());
        registry.register(new HighPriorityFactory());

        VirtualOrgAgentConfig config = createConfig("priority-agent");

        Agent agent = registry.create("shared-template", config);

        // HighPriorityFactory should be selected (priority 100 > 10)
        assertThat(agent.getFrameworkCapabilities()).contains("high-priority");
    }

    @Test
    @DisplayName("Should filter factories by domain")
    void shouldFilterFactoriesByDomain() {
        registry.register(new DomainSpecificFactory("software"));
        registry.register(new DomainSpecificFactory("healthcare"));

        var softwareFactories = registry.getFactoriesByDomain("software");
        assertThat(softwareFactories).hasSize(1);
    }

    @Test
    @DisplayName("Should list supported templates")
    void shouldListSupportedTemplates() {
        registry.register(new TestAgentFactory());
        registry.register(new HighPriorityFactory());

        Set<String> templates = registry.getSupportedTemplates();
        assertThat(templates).contains("test-template", "shared-template");
    }

    @Test
    @DisplayName("Should count registered factories")
    void shouldCountRegisteredFactories() {
        assertThat(registry.getFactoryCount()).isEqualTo(0);

        registry.register(new TestAgentFactory());
        assertThat(registry.getFactoryCount()).isEqualTo(1);

        registry.register(new HighPriorityFactory());
        assertThat(registry.getFactoryCount()).isEqualTo(2);
    }

    // Test agent factories

    private static class TestAgentFactory implements AgentFactory {
        @Override
        public Optional<Agent> createAgent(String template, VirtualOrgAgentConfig config) {
            Agent agent = Agent.builder()
                    .id(config.getName())
                    .name(config.getName())
                    .capabilities("test")
                    .build();
            return Optional.of(agent);
        }

        @Override
        public boolean supports(String template) {
            return "test-template".equals(template);
        }

        @Override
        public Set<String> getSupportedTemplates() {
            return Set.of("test-template");
        }
    }

    private static class LowPriorityFactory implements AgentFactory {
        @Override
        public Optional<Agent> createAgent(String template, VirtualOrgAgentConfig config) {
            Agent agent = Agent.builder()
                    .id(config.getName())
                    .name(config.getName())
                    .capabilities("low-priority")
                    .build();
            return Optional.of(agent);
        }

        @Override
        public boolean supports(String template) {
            return "shared-template".equals(template);
        }

        @Override
        public int getPriority() {
            return 10;
        }

        @Override
        public Set<String> getSupportedTemplates() {
            return Set.of("shared-template");
        }
    }

    private static class HighPriorityFactory implements AgentFactory {
        @Override
        public Optional<Agent> createAgent(String template, VirtualOrgAgentConfig config) {
            Agent agent = Agent.builder()
                    .id(config.getName())
                    .name(config.getName())
                    .capabilities("high-priority")
                    .build();
            return Optional.of(agent);
        }

        @Override
        public boolean supports(String template) {
            return "shared-template".equals(template);
        }

        @Override
        public int getPriority() {
            return 100;
        }

        @Override
        public Set<String> getSupportedTemplates() {
            return Set.of("shared-template");
        }
    }

    private static class DomainSpecificFactory implements AgentFactory {
        private final String domain;

        DomainSpecificFactory(String domain) {
            this.domain = domain;
        }

        @Override
        public Optional<Agent> createAgent(String template, VirtualOrgAgentConfig config) {
            Agent agent = Agent.builder()
                    .id(config.getName())
                    .name(config.getName())
                    .capabilities("domain:" + domain)
                    .build();
            return Optional.of(agent);
        }

        @Override
        public boolean supports(String template) {
            return true;
        }

        @Override
        public String getDomain() {
            return domain;
        }

        @Override
        public Set<String> getSupportedTemplates() {
            return Set.of(domain + "-agent");
        }
    }
}
