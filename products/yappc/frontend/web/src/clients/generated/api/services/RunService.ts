/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { PromoteRequest } from '../models/PromoteRequest';
import type { RetryRunRequest } from '../models/RetryRunRequest';
import type { RollbackRequest } from '../models/RollbackRequest';
import type { RunRequest } from '../models/RunRequest';
import type { RunWithObservationRequest } from '../models/RunWithObservationRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class RunService {
    /**
     * Execute a YAPPC run
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Run result
     * @throws ApiError
     */
    public static runArtifacts(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: RunRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/run',
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Execute a run with embedded observation collection
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Run result with observations
     * @throws ApiError
     */
    public static runWithObservation(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: RunWithObservationRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/run/with-observation',
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Retry a failed run with a new run specification
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Retry result
     * @throws ApiError
     */
    public static retryRun(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: RetryRunRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/run/retry',
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Rollback the last executed run
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Rollback result
     * @throws ApiError
     */
    public static rollbackRun(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: RollbackRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/run/rollback',
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Promote the last validated run to the next environment
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Promotion result
     * @throws ApiError
     */
    public static promoteRun(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: PromoteRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/run/promote',
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * List recent runs for a project
     * @param projectId
     * @returns any Run list
     * @throws ApiError
     */
    public static listProjectRuns(
        projectId: string,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/projects/{projectId}/runs',
            path: {
                'projectId': projectId,
            },
        });
    }
}
