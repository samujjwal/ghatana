/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ActivityEntry } from '../models/ActivityEntry';
import type { AvailableProjectsResponse } from '../models/AvailableProjectsResponse';
import type { CreateProjectRequest } from '../models/CreateProjectRequest';
import type { ExecuteProjectDashboardActionRequest } from '../models/ExecuteProjectDashboardActionRequest';
import type { ExecuteProjectDashboardActionResponse } from '../models/ExecuteProjectDashboardActionResponse';
import type { IncludedProjectResponse } from '../models/IncludedProjectResponse';
import type { IncludeProjectRequest } from '../models/IncludeProjectRequest';
import type { NameSuggestionResponse } from '../models/NameSuggestionResponse';
import type { ProjectAiCost } from '../models/ProjectAiCost';
import type { ProjectCapabilities } from '../models/ProjectCapabilities';
import type { ProjectDashboardActionsResponse } from '../models/ProjectDashboardActionsResponse';
import type { ProjectResponse } from '../models/ProjectResponse';
import type { ProjectSetupSuggestion } from '../models/ProjectSetupSuggestion';
import type { ProjectSetupSuggestionRequest } from '../models/ProjectSetupSuggestionRequest';
import type { ProjectsResponse } from '../models/ProjectsResponse';
import type { ProjectType } from '../models/ProjectType';
import type { UpdateProjectRequest } from '../models/UpdateProjectRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ProjectsService {
    /**
     * List owned and included projects for a workspace
     * @param workspaceId
     * @returns ProjectsResponse Workspace project list
     * @throws ApiError
     */
    public static listProjects(
        workspaceId: string,
    ): CancelablePromise<ProjectsResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/projects',
            query: {
                'workspaceId': workspaceId,
            },
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Create a project owned by a workspace
     * @param requestBody
     * @returns ProjectResponse Project created
     * @throws ApiError
     */
    public static createProject(
        requestBody: CreateProjectRequest,
    ): CancelablePromise<ProjectResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/projects',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * List backend-backed dashboard blocker, review, and continuation actions
     * @param workspaceId
     * @returns ProjectDashboardActionsResponse Dashboard action summary
     * @throws ApiError
     */
    public static listProjectDashboardActions(
        workspaceId: string,
    ): CancelablePromise<ProjectDashboardActionsResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/projects/dashboard-actions',
            query: {
                'workspaceId': workspaceId,
            },
            errors: {
                400: `Bad request — invalid or missing parameters`,
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * Execute a safe backend-backed dashboard action
     * @param projectId
     * @param requestBody
     * @returns ExecuteProjectDashboardActionResponse Safe dashboard action executed
     * @throws ApiError
     */
    public static executeProjectDashboardAction(
        projectId: string,
        requestBody: ExecuteProjectDashboardActionRequest,
    ): CancelablePromise<ExecuteProjectDashboardActionResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/projects/{projectId}/dashboard-actions/execute',
            path: {
                'projectId': projectId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                401: `Authentication required or token invalid`,
                403: `Permission denied`,
                404: `Resource not found`,
                409: `Action requires review before one-click execution`,
            },
        });
    }
    /**
     * Get a single project with ownership context
     * @param projectId
     * @param workspaceId
     * @returns ProjectResponse Project detail
     * @throws ApiError
     */
    public static getProject(
        projectId: string,
        workspaceId?: string,
    ): CancelablePromise<ProjectResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/projects/{projectId}',
            path: {
                'projectId': projectId,
            },
            query: {
                'workspaceId': workspaceId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Update a project owned by the current workspace
     * @param projectId
     * @param workspaceId
     * @param requestBody
     * @returns ProjectResponse Project updated
     * @throws ApiError
     */
    public static updateProject(
        projectId: string,
        workspaceId: string,
        requestBody: UpdateProjectRequest,
    ): CancelablePromise<ProjectResponse> {
        return __request(OpenAPI, {
            method: 'PATCH',
            url: '/api/projects/{projectId}',
            path: {
                'projectId': projectId,
            },
            query: {
                'workspaceId': workspaceId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                403: `Current workspace does not own the project`,
                404: `Resource not found`,
            },
        });
    }
    /**
     * Delete a project owned by the current workspace
     * @param projectId
     * @param workspaceId
     * @returns void
     * @throws ApiError
     */
    public static deleteProject(
        projectId: string,
        workspaceId: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/api/projects/{projectId}',
            path: {
                'projectId': projectId,
            },
            query: {
                'workspaceId': workspaceId,
            },
            errors: {
                400: `Bad request — invalid or missing parameters`,
                403: `Current workspace does not own the project`,
                404: `Resource not found`,
            },
        });
    }
    /**
     * Include a read-only project in another workspace
     * @param requestBody
     * @returns IncludedProjectResponse Project inclusion created
     * @throws ApiError
     */
    public static includeProject(
        requestBody: IncludeProjectRequest,
    ): CancelablePromise<IncludedProjectResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/projects/include',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                401: `Authentication required or token invalid`,
                404: `Resource not found`,
            },
        });
    }
    /**
     * Remove a project inclusion from a workspace
     * @param requestBody
     * @returns void
     * @throws ApiError
     */
    public static removeIncludedProject(
        requestBody: IncludeProjectRequest,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/api/projects/include',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * List projects that can be included in a workspace
     * @param workspaceId
     * @returns AvailableProjectsResponse Available projects
     * @throws ApiError
     */
    public static listAvailableProjectsForInclusion(
        workspaceId: string,
    ): CancelablePromise<AvailableProjectsResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/projects/available-for-inclusion',
            query: {
                'workspaceId': workspaceId,
            },
            errors: {
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * Suggest a project name for a workspace
     * @param workspaceId
     * @param type
     * @returns NameSuggestionResponse Suggested project name
     * @throws ApiError
     */
    public static suggestProjectName(
        workspaceId: string,
        type?: ProjectType,
    ): CancelablePromise<NameSuggestionResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/projects/suggest-name',
            query: {
                'workspaceId': workspaceId,
                'type': type,
            },
        });
    }
    /**
     * Suggest initial project setup from description and workspace context
     * @param requestBody
     * @returns ProjectSetupSuggestion Project setup suggestion
     * @throws ApiError
     */
    public static suggestProjectSetup(
        requestBody: ProjectSetupSuggestionRequest,
    ): CancelablePromise<ProjectSetupSuggestion> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/projects/setup-suggestion',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                404: `Resource not found`,
            },
        });
    }
    /**
     * Save canvas snapshot for a project
     * @param requestBody
     * @returns any Canvas saved
     * @throws ApiError
     */
    public static saveCanvas(
        requestBody: Record<string, any>,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/canvas',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Get activity log for a project
     * @param projectId
     * @returns ActivityEntry Activity log
     * @throws ApiError
     */
    public static getProjectActivity(
        projectId: string,
    ): CancelablePromise<Array<ActivityEntry>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/projects/{projectId}/activity',
            path: {
                'projectId': projectId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Get automation cost for a project
     * @param projectId
     * @returns ProjectAiCost Automation cost breakdown
     * @throws ApiError
     */
    public static getProjectAiCost(
        projectId: string,
    ): CancelablePromise<ProjectAiCost> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/projects/{projectId}/ai-cost',
            path: {
                'projectId': projectId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Get capability gates for a project
     * @param projectId
     * @returns ProjectCapabilities Capabilities
     * @throws ApiError
     */
    public static getProjectCapabilities(
        projectId: string,
    ): CancelablePromise<ProjectCapabilities> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/projects/{projectId}/capabilities',
            path: {
                'projectId': projectId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Update capability gates for a project
     * @param projectId
     * @param requestBody
     * @returns ProjectCapabilities Capabilities updated
     * @throws ApiError
     */
    public static updateProjectCapabilities(
        projectId: string,
        requestBody: ProjectCapabilities,
    ): CancelablePromise<ProjectCapabilities> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/projects/{projectId}/capabilities',
            path: {
                'projectId': projectId,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Refresh project recommendations
     * @param projectId
     * @returns any Refresh triggered
     * @throws ApiError
     */
    public static refreshProjectAiSuggestions(
        projectId: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/projects/{projectId}/refresh-ai',
            path: {
                'projectId': projectId,
            },
        });
    }
    /**
     * Get collaboration activity feed
     * @returns any Activity feed
     * @throws ApiError
     */
    public static getActivityFeed(): CancelablePromise<Array<Record<string, any>>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/activity',
        });
    }
    /**
     * Get billing summary view model
     * @returns any Billing summary
     * @throws ApiError
     */
    public static getBillingSummary(): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/billing',
        });
    }
    /**
     * Get collaboration calendar events
     * @returns any Calendar events
     * @throws ApiError
     */
    public static getCalendarEvents(): CancelablePromise<Array<Record<string, any>>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/calendar/events',
        });
    }
    /**
     * List collaboration message channels
     * @returns any Channel list
     * @throws ApiError
     */
    public static listMessageChannels(): CancelablePromise<Array<Record<string, any>>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/messages/channels',
        });
    }
    /**
     * Get messages for a channel
     * @param channelId
     * @returns any Channel messages
     * @throws ApiError
     */
    public static getMessageChannel(
        channelId: string,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/messages/channels/{channelId}',
            path: {
                'channelId': channelId,
            },
        });
    }
    /**
     * Get on-call schedule
     * @returns any On-call schedule
     * @throws ApiError
     */
    public static getOncallSchedule(): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/oncall',
        });
    }
    /**
     * List artifacts for a project
     * @param projectId
     * @returns any Project artifact list
     * @throws ApiError
     */
    public static listProjectArtifacts(
        projectId: string,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/projects/{projectId}/artifacts',
            path: {
                'projectId': projectId,
            },
        });
    }
    /**
     * Get project backlog
     * @param projectId
     * @returns any Backlog items
     * @throws ApiError
     */
    public static getProjectBacklog(
        projectId: string,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/projects/{projectId}/backlog',
            path: {
                'projectId': projectId,
            },
        });
    }
    /**
     * Get current sprint for a project
     * @param projectId
     * @returns any Current sprint
     * @throws ApiError
     */
    public static getCurrentProjectSprint(
        projectId: string,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/projects/{projectId}/sprints/current',
            path: {
                'projectId': projectId,
            },
        });
    }
    /**
     * Get service topology model
     * @returns any Service topology
     * @throws ApiError
     */
    public static getServiceTopology(): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/services/topology',
        });
    }
    /**
     * Get team hub view model
     * @returns any Team hub payload
     * @throws ApiError
     */
    public static getTeamHub(): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/teams/hub',
        });
    }
}
