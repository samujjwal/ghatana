package com.ghatana.agent.workflow;

/**
 * Enumeration of workflow agent roles for DevSecOps automation.
 *
 * <p>Each role represents a persona-based agent type that can be assigned
 * to workflow tasks. Roles map to specific capabilities and behaviors.
 *
 * <p><b>TypeScript Alignment:</b> This enum matches the TypeScript
 * {@code WorkflowAgentRole} type from {@code @yappc/types/devsecops/workflow-automation}.
 *
 * @doc.type enum
 * @doc.purpose Workflow agent role classification
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum WorkflowAgentRole {

    /**
     * Manages and assigns tasks to appropriate agents.
     */
    TASK_MANAGER("task-manager", "Task Manager", "Manages and assigns tasks to appropriate agents"),

    /**
     * Analyzes code for quality, security, and best practices.
     */
    CODE_REVIEWER("code-reviewer", "Code Reviewer", "Analyzes code for quality, security, and best practices"),

    /**
     * Generates and maintains test cases.
     */
    TEST_WRITER("test-writer", "Test Writer", "Generates and maintains test cases"),

    /**
     * Writes and updates documentation.
     */
    DOCUMENTATION_AGENT("documentation", "Documentation Agent", "Writes and updates documentation"),

    /**
     * Identifies and fixes security vulnerabilities.
     */
    SECURITY_SCANNER("security-scanner", "Security Scanner", "Identifies and fixes security vulnerabilities"),

    /**
     * Optimizes code performance and identifies bottlenecks.
     */
    PERFORMANCE_OPTIMIZER("performance-optimizer", "Performance Optimizer", "Optimizes code performance"),

    /**
     * Manages release and deployment workflows.
     */
    RELEASE_MANAGER("release-manager", "Release Manager", "Manages release and deployment workflows"),

    /**
     * Monitors systems and responds to incidents.
     */
    INCIDENT_RESPONDER("incident-responder", "Incident Responder", "Monitors and responds to incidents"),

    /**
     * Manages infrastructure as code.
     */
    INFRASTRUCTURE_AGENT("infrastructure", "Infrastructure Agent", "Manages infrastructure as code"),

    /**
     * Handles compliance and audit requirements.
     */
    COMPLIANCE_AUDITOR("compliance-auditor", "Compliance Auditor", "Handles compliance and audit requirements"),

    /**
     * General-purpose agent for miscellaneous tasks.
     */
    GENERAL("general", "General Agent", "General-purpose agent");

    private final String code;
    private final String displayName;
    private final String description;

    WorkflowAgentRole(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Returns the code identifier used in APIs and storage.
     *
     * @return The code (e.g., "code-reviewer")
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the human-readable display name.
     *
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the role description.
     *
     * @return The description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Finds a role by its code.
     *
     * @param code The code to search for
     * @return The matching role, or {@link #GENERAL} if not found
     */
    public static WorkflowAgentRole fromCode(String code) {
        if (code == null) {
            return GENERAL;
        }
        for (WorkflowAgentRole role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        return GENERAL;
    }
}
