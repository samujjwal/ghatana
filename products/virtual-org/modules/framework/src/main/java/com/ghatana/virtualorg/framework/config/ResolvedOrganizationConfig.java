/*
 * Copyright (c) 2025 Ghatana Platforms, Inc. All rights reserved.
 *
 * PROPRIETARY/CONFIDENTIAL. Use is subject to the terms of a separate
 * license agreement between you and Ghatana Platforms, Inc. You may not
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of this software, in whole or in part, except as expressly
 * permitted under the applicable written license agreement.
 *
 * Unauthorized use, reproduction, or distribution of this software, or any
 * portion of it, may result in severe civil and criminal penalties, and
 * will be prosecuted to the maximum extent possible under the law.
 */
package com.ghatana.virtualorg.framework.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a fully resolved organization configuration with all references loaded.
 * Contains the main organization config plus all referenced departments, agents,
 * workflows, actions, and personas.
 *
 * @doc.type record
 * @doc.purpose Encapsulate fully resolved organization configuration
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record ResolvedOrganizationConfig(
        OrganizationConfig organization,
        List<DepartmentConfig> departments,
        List<VirtualOrgAgentConfig> agents,
        List<WorkflowConfig> workflows,
        List<ActionConfig> actions,
        List<PersonaConfig> personas
) {

    /**
     * Creates a resolved organization config with all components.
     */
    public ResolvedOrganizationConfig {
        departments = departments != null ? new ArrayList<>(departments) : List.of();
        agents = agents != null ? new ArrayList<>(agents) : List.of();
        workflows = workflows != null ? new ArrayList<>(workflows) : List.of();
        actions = actions != null ? new ArrayList<>(actions) : List.of();
        personas = personas != null ? new ArrayList<>(personas) : List.of();
    }

    /**
     * Creates a minimal resolved config with only organization and departments.
     *
     * @param organization organization config
     * @param departments department configs
     */
    public ResolvedOrganizationConfig(
            OrganizationConfig organization,
            List<DepartmentConfig> departments) {
        this(organization, departments, List.<VirtualOrgAgentConfig>of(), List.of(), List.of(), List.of());
    }

    /**
     * Creates a resolved config with organization, departments, and workflows.
     *
     * @param organization organization config
     * @param departments department configs
     * @param workflows workflow configs
     */
    public ResolvedOrganizationConfig(
            OrganizationConfig organization,
            List<DepartmentConfig> departments,
            List<WorkflowConfig> workflows) {
        this(organization, departments, List.of(), workflows, List.of(), List.of());
    }

    /**
     * Gets the organization display name.
     *
     * @return display name
     */
    public String getDisplayName() {
        return organization.spec().displayName();
    }

    /**
     * Gets the number of departments.
     *
     * @return department count
     */
    public int getDepartmentCount() {
        return departments.size();
    }

    /**
     * Gets the number of agents.
     *
     * @return agent count
     */
    public int getAgentCount() {
        return agents.size();
    }

    /**
     * Gets the number of workflows.
     *
     * @return workflow count
     */
    public int getWorkflowCount() {
        return workflows.size();
    }

    /**
     * Gets the number of actions.
     *
     * @return action count
     */
    public int getActionCount() {
        return actions.size();
    }

    /**
     * Gets the number of personas.
     *
     * @return persona count
     */
    public int getPersonaCount() {
        return personas.size();
    }

    /**
     * Checks if the configuration is complete (has all referenced components).
     *
     * @return true if all components are loaded
     */
    public boolean isComplete() {
        return !departments.isEmpty() || !workflows.isEmpty();
    }

    @Override
    public String toString() {
        return String.format(
                "ResolvedOrganizationConfig[org=%s, departments=%d, agents=%d, workflows=%d, actions=%d, personas=%d]",
                getDisplayName(),
                getDepartmentCount(),
                getAgentCount(),
                getWorkflowCount(),
                getActionCount(),
                getPersonaCount()
        );
    }
}

