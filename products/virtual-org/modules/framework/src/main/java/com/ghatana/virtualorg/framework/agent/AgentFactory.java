package com.ghatana.virtualorg.framework.agent;

import com.ghatana.virtualorg.framework.config.VirtualOrgAgentConfig;
import java.util.Optional;
import java.util.Set;

/**
 * Factory interface for creating domain-specific agents.
 *
 * <p><b>Purpose</b><br>
 * The AgentFactory is the core extension point for injecting domain-specific
 * agent behavior into the generic Virtual-Org framework. Products like
 * Software-Org or Healthcare-Org implement this interface to create their
 * specialized agents (e.g., CodeReviewer, TriageNurse).
 *
 * <p><b>SPI Integration</b><br>
 * Factories are discovered via Java ServiceLoader. Create a file at:
 * {@code META-INF/services/com.ghatana.virtualorg.framework.agent.AgentFactory}
 * containing the fully qualified class name of your implementation.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * public class SoftwareAgentFactory implements AgentFactory {
 *     @Override
 *     public Set<String> getSupportedTemplates() {
 *         return Set.of("CodeReviewer", "ReleaseManager", "TechLead");
 *     }
 *
 *     @Override
 *     public Optional<Agent> createAgent(String template, VirtualOrgAgentConfig config) {
 *         return switch (template) {
 *             case "CodeReviewer" -> Optional.of(new CodeReviewerAgent(config));
 *             case "ReleaseManager" -> Optional.of(new ReleaseManagerAgent(config));
 *             default -> Optional.empty();
 *         };
 *     }
 * }
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Factory interface for creating domain-specific agents
 * @doc.layer platform
 * @doc.pattern Factory, SPI
 */
public interface AgentFactory {

    /**
     * Gets the set of agent templates this factory supports.
     *
     * @return set of template names (e.g., "CodeReviewer", "TriageNurse")
     */
    Set<String> getSupportedTemplates();

    /**
     * Gets the domain/product this factory belongs to.
     *
     * @return domain name (e.g., "software-org", "healthcare-org")
     */
    default String getDomain() {
        return "default";
    }

    /**
     * Gets the priority of this factory (higher = checked first).
     * Useful when multiple factories support the same template.
     *
     * @return priority (default 0)
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Creates an agent based on the provided template and configuration.
     *
     * @param template The template name (e.g., "CodeReviewer", "TriageNurse").
     * @param config   The configuration for the agent.
     * @return An Optional containing the created Agent, or empty if this factory doesn't support the template.
     */
    Optional<Agent> createAgent(String template, VirtualOrgAgentConfig config);

    /**
     * Checks if this factory supports a template.
     *
     * @param template the template name
     * @return true if supported
     */
    default boolean supports(String template) {
        return getSupportedTemplates().contains(template);
    }
}

