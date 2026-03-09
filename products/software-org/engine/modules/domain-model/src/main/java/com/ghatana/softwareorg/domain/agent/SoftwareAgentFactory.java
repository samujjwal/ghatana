package com.ghatana.softwareorg.domain.agent;

import com.ghatana.virtualorg.framework.agent.Agent;
import com.ghatana.virtualorg.framework.agent.AgentFactory;
import com.ghatana.virtualorg.framework.config.VirtualOrgAgentConfig;

import java.util.Optional;
import java.util.Set;

/**
 * Factory for creating software engineering domain agents.
 *
 * <p><b>Purpose</b><br>
 * Creates specialized agents for software engineering tasks such as
 * code review, release management, architecture, etc.
 *
 * <p><b>Supported Templates</b><br>
 * - CodeReviewer: Reviews code changes and pull requests
 * - ReleaseManager: Manages deployments and releases
 * - TechLead: Technical leadership and decision making
 * - Architect: System design and architecture
 * - QAEngineer: Testing and quality assurance
 *
 * <p><b>SPI Registration</b><br>
 * This factory is registered via SPI. Create:
 * {@code META-INF/services/com.ghatana.virtualorg.framework.agent.AgentFactory}
 *
 * @doc.type class
 * @doc.purpose Software engineering agent factory
 * @doc.layer product
 * @doc.pattern Factory, SPI
 */
public class SoftwareAgentFactory implements AgentFactory {

    public static final String TEMPLATE_CODE_REVIEWER = "CodeReviewer";
    public static final String TEMPLATE_RELEASE_MANAGER = "ReleaseManager";
    public static final String TEMPLATE_TECH_LEAD = "TechLead";
    public static final String TEMPLATE_ARCHITECT = "Architect";
    public static final String TEMPLATE_QA_ENGINEER = "QAEngineer";
    public static final String TEMPLATE_DEVOPS_ENGINEER = "DevOpsEngineer";
    public static final String TEMPLATE_FRONTEND_DEV = "FrontendDeveloper";
    public static final String TEMPLATE_BACKEND_DEV = "BackendDeveloper";

    private static final Set<String> SUPPORTED_TEMPLATES = Set.of(
            TEMPLATE_CODE_REVIEWER,
            TEMPLATE_RELEASE_MANAGER,
            TEMPLATE_TECH_LEAD,
            TEMPLATE_ARCHITECT,
            TEMPLATE_QA_ENGINEER,
            TEMPLATE_DEVOPS_ENGINEER,
            TEMPLATE_FRONTEND_DEV,
            TEMPLATE_BACKEND_DEV
    );

    @Override
    public Set<String> getSupportedTemplates() {
        return SUPPORTED_TEMPLATES;
    }

    @Override
    public String getDomain() {
        return "software-org";
    }

    @Override
    public int getPriority() {
        return 100; // Higher priority than default
    }

    @Override
    public Optional<Agent> createAgent(String template, VirtualOrgAgentConfig config) {
        if (!supports(template)) {
            return Optional.empty();
        }

        return switch (template) {
            case TEMPLATE_CODE_REVIEWER -> createCodeReviewerAgent(config);
            case TEMPLATE_RELEASE_MANAGER -> createReleaseManagerAgent(config);
            case TEMPLATE_TECH_LEAD -> createTechLeadAgent(config);
            case TEMPLATE_ARCHITECT -> createArchitectAgent(config);
            case TEMPLATE_QA_ENGINEER -> createQAEngineerAgent(config);
            case TEMPLATE_DEVOPS_ENGINEER -> createDevOpsEngineerAgent(config);
            case TEMPLATE_FRONTEND_DEV -> createFrontendDevAgent(config);
            case TEMPLATE_BACKEND_DEV -> createBackendDevAgent(config);
            default -> Optional.empty();
        };
    }

    private Optional<Agent> createCodeReviewerAgent(VirtualOrgAgentConfig config) {
        return Optional.of(Agent.builder()
                .id(config.getName())
                .name(getDisplayName(config, "Code Reviewer"))
                .capabilities("code-review", "pull-request-review", "feedback", "java", "typescript")
                .build());
    }

    private Optional<Agent> createReleaseManagerAgent(VirtualOrgAgentConfig config) {
        return Optional.of(Agent.builder()
                .id(config.getName())
                .name(getDisplayName(config, "Release Manager"))
                .capabilities("release-management", "deployment", "rollback", "monitoring")
                .build());
    }

    private Optional<Agent> createTechLeadAgent(VirtualOrgAgentConfig config) {
        return Optional.of(Agent.builder()
                .id(config.getName())
                .name(getDisplayName(config, "Tech Lead"))
                .capabilities("technical-leadership", "code-review", "architecture", "mentoring", "decision-making")
                .build());
    }

    private Optional<Agent> createArchitectAgent(VirtualOrgAgentConfig config) {
        return Optional.of(Agent.builder()
                .id(config.getName())
                .name(getDisplayName(config, "Architect"))
                .capabilities("system-design", "architecture", "technical-strategy", "standards")
                .build());
    }

    private Optional<Agent> createQAEngineerAgent(VirtualOrgAgentConfig config) {
        return Optional.of(Agent.builder()
                .id(config.getName())
                .name(getDisplayName(config, "QA Engineer"))
                .capabilities("testing", "test-automation", "quality-assurance", "bug-reporting")
                .build());
    }

    private Optional<Agent> createDevOpsEngineerAgent(VirtualOrgAgentConfig config) {
        return Optional.of(Agent.builder()
                .id(config.getName())
                .name(getDisplayName(config, "DevOps Engineer"))
                .capabilities("ci-cd", "infrastructure", "kubernetes", "docker", "monitoring")
                .build());
    }

    private Optional<Agent> createFrontendDevAgent(VirtualOrgAgentConfig config) {
        return Optional.of(Agent.builder()
                .id(config.getName())
                .name(getDisplayName(config, "Frontend Developer"))
                .capabilities("frontend", "react", "typescript", "css", "ui-ux")
                .build());
    }

    private Optional<Agent> createBackendDevAgent(VirtualOrgAgentConfig config) {
        return Optional.of(Agent.builder()
                .id(config.getName())
                .name(getDisplayName(config, "Backend Developer"))
                .capabilities("backend", "java", "api-design", "database", "microservices")
                .build());
    }

    private String getDisplayName(VirtualOrgAgentConfig config, String defaultName) {
        if (config.getDisplayName() != null && !config.getDisplayName().isBlank()) {
            return config.getDisplayName();
        }
        return defaultName;
    }
}
