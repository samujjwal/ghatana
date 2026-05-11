/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.dashboard;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.api.DashboardAction;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Service for building backend-derived dashboard actions.
 *
 * <p>This service determines which actions should be available on the dashboard
 * based on project state, user capabilities, and lifecycle phase.
 *
 * @doc.type interface
 * @doc.purpose Dashboard action service interface
 * @doc.layer service
 * @doc.pattern Service
 */
public interface DashboardActionService {

    /**
     * Builds dashboard actions for all projects in a workspace.
     *
     * @param workspaceId the workspace ID
     * @param principal the user principal
     * @param correlationId optional correlation ID for tracing
     * @return a promise resolving to a list of project dashboard actions
     */
    Promise<List<DashboardAction.ProjectDashboardActions>> buildDashboardActions(
            String workspaceId,
            Principal principal,
            String correlationId
    );

    /**
     * Builds dashboard actions for a specific project.
     *
     * @param projectId the project ID
     * @param workspaceId the workspace ID
     * @param principal the user principal
     * @param correlationId optional correlation ID for tracing
     * @return a promise resolving to the project dashboard actions
     */
    Promise<DashboardAction.ProjectDashboardActions> buildProjectDashboardActions(
            String projectId,
            String workspaceId,
            Principal principal,
            String correlationId
    );
}
