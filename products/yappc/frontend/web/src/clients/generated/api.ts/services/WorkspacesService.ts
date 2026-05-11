/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AddMemberRequest } from '../models/AddMemberRequest';
import type { CreateWorkspaceRequest } from '../models/CreateWorkspaceRequest';
import type { NameSuggestionResponse } from '../models/NameSuggestionResponse';
import type { UpdateMemberRoleRequest } from '../models/UpdateMemberRoleRequest';
import type { UpdateWorkspaceRequest } from '../models/UpdateWorkspaceRequest';
import type { WorkspaceDetailResponse } from '../models/WorkspaceDetailResponse';
import type { WorkspaceListResponse } from '../models/WorkspaceListResponse';
import type { WorkspaceMember } from '../models/WorkspaceMember';
import type { WorkspaceResponse } from '../models/WorkspaceResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class WorkspacesService {
    /**
     * List workspaces for the authenticated user
     * @returns WorkspaceListResponse Workspace list
     * @throws ApiError
     */
    public static listWorkspaces(): CancelablePromise<WorkspaceListResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/workspaces',
            errors: {
                401: `Authentication required or token invalid`,
                503: `Database service unavailable`,
            },
        });
    }
    /**
     * Create a workspace and optional starter project
     * @param requestBody
     * @returns WorkspaceResponse Workspace created
     * @throws ApiError
     */
    public static createWorkspace(
        requestBody: CreateWorkspaceRequest,
    ): CancelablePromise<WorkspaceResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/workspaces',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * Get a workspace with owned and included projects
     * @param workspaceId
     * @returns WorkspaceDetailResponse Workspace detail
     * @throws ApiError
     */
    public static getWorkspace(
        workspaceId: string,
    ): CancelablePromise<WorkspaceDetailResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/workspaces/{workspaceId}',
            path: {
                'workspaceId': workspaceId,
            },
            errors: {
                404: `Resource not found`,
                503: `Database service unavailable`,
            },
        });
    }
    /**
     * Update workspace metadata
     * @param workspaceId
     * @param requestBody
     * @returns WorkspaceResponse Workspace updated
     * @throws ApiError
     */
    public static updateWorkspace(
        workspaceId: string,
        requestBody: UpdateWorkspaceRequest,
    ): CancelablePromise<WorkspaceResponse> {
        return __request(OpenAPI, {
            method: 'PATCH',
            url: '/api/workspaces/{workspaceId}',
            path: {
                'workspaceId': workspaceId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Delete a workspace
     * @param workspaceId
     * @returns void
     * @throws ApiError
     */
    public static deleteWorkspace(
        workspaceId: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/api/workspaces/{workspaceId}',
            path: {
                'workspaceId': workspaceId,
            },
            errors: {
                400: `Bad request — invalid or missing parameters`,
                404: `Resource not found`,
            },
        });
    }
    /**
     * Suggest a workspace name
     * @returns NameSuggestionResponse Suggested workspace name
     * @throws ApiError
     */
    public static suggestWorkspaceName(): CancelablePromise<NameSuggestionResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/workspaces/suggest-name',
            errors: {
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * Refresh workspace recommendations
     * @param workspaceId
     * @returns any Refresh triggered
     * @throws ApiError
     */
    public static refreshWorkspaceAiSuggestions(
        workspaceId: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/workspaces/{workspaceId}/refresh-ai',
            path: {
                'workspaceId': workspaceId,
            },
        });
    }
    /**
     * List workspace members
     * @param workspaceId
     * @returns WorkspaceMember Members list
     * @throws ApiError
     */
    public static listWorkspaceMembers(
        workspaceId: string,
    ): CancelablePromise<Array<WorkspaceMember>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/workspaces/{workspaceId}/members',
            path: {
                'workspaceId': workspaceId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Add a member to a workspace
     * @param workspaceId
     * @param requestBody
     * @returns any Member added
     * @throws ApiError
     */
    public static addWorkspaceMember(
        workspaceId: string,
        requestBody: AddMemberRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/workspaces/{workspaceId}/members',
            path: {
                'workspaceId': workspaceId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Update workspace member role
     * @param workspaceId
     * @param userId
     * @param requestBody
     * @returns any Role updated
     * @throws ApiError
     */
    public static updateWorkspaceMemberRole(
        workspaceId: string,
        userId: string,
        requestBody: UpdateMemberRoleRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'PATCH',
            url: '/workspaces/{workspaceId}/members/{userId}/role',
            path: {
                'workspaceId': workspaceId,
                'userId': userId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                404: `Resource not found`,
            },
        });
    }
    /**
     * Remove a member from a workspace
     * @param workspaceId
     * @param userId
     * @returns void
     * @throws ApiError
     */
    public static removeWorkspaceMember(
        workspaceId: string,
        userId: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/workspaces/{workspaceId}/members/{userId}',
            path: {
                'workspaceId': workspaceId,
                'userId': userId,
            },
            errors: {
                400: `Bad request — invalid or missing parameters`,
                404: `Resource not found`,
            },
        });
    }
}
