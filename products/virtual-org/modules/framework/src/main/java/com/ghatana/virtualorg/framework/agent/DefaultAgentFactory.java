package com.ghatana.virtualorg.framework.agent;

import com.ghatana.virtualorg.framework.config.VirtualOrgAgentConfig;

import java.util.Optional;
import java.util.Set;

/**
 * Default agent factory that creates generic agents.
 *
 * <p><b>Purpose</b><br>
 * The DefaultAgentFactory serves as a fallback for agent creation when
 * no domain-specific factory is registered. It creates basic Agent
 * instances from configuration without any specialized behavior.
 *
 * <p><b>Priority</b><br>
 * This factory has the lowest priority (-1000) so domain-specific
 * factories are always checked first.
 *
 * @doc.type class
 * @doc.purpose Default/fallback agent factory
 * @doc.layer platform
 * @doc.pattern Factory
 */
public class DefaultAgentFactory implements AgentFactory {

    /**
     * The "default" template name for generic agents.
     */
    public static final String DEFAULT_TEMPLATE = "default";

    @Override
    public Set<String> getSupportedTemplates() {
        return Set.of(DEFAULT_TEMPLATE);
    }

    @Override
    public String getDomain() {
        return "framework";
    }

    @Override
    public int getPriority() {
        return -1000; // Lowest priority - always checked last
    }

    @Override
    public Optional<Agent> createAgent(String template, VirtualOrgAgentConfig config) {
        if (!supports(template) && !DEFAULT_TEMPLATE.equals(template)) {
            return Optional.empty();
        }

        Agent.Builder builder = Agent.builder()
                .id(config.getName())
                .name(config.getDisplayName() != null ? config.getDisplayName() : config.getName());

        // Add capabilities if present
        if (!config.getPrimaryCapabilities().isEmpty()) {
            builder.capabilities(config.getPrimaryCapabilities().toArray(String[]::new));
        }

        // Set department if present
        if (config.getDepartment() != null) {
            builder.department(config.getDepartment());
        }

        return Optional.of(builder.build());
    }

    /**
     * For the default factory, we accept any template as a fallback.
     */
    @Override
    public boolean supports(String template) {
        return DEFAULT_TEMPLATE.equals(template);
    }
}
