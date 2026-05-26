/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { GenerateArtifactsRequest } from '../models/GenerateArtifactsRequest';
import type { GenerateArtifactsResponse } from '../models/GenerateArtifactsResponse';
import type { GenerateReviewDecisionRequest } from '../models/GenerateReviewDecisionRequest';
import type { GenerateReviewDecisionResponse } from '../models/GenerateReviewDecisionResponse';
import type { KernelProductUnitIntentRequest } from '../models/KernelProductUnitIntentRequest';
import type { KernelProductUnitIntentResponse } from '../models/KernelProductUnitIntentResponse';
import type { RegenerateDiffRequest } from '../models/RegenerateDiffRequest';
import type { RegenerateDiffResponse } from '../models/RegenerateDiffResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class GenerateService {
    /**
     * Generate project artifacts (code, config, CI)
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns GenerateArtifactsResponse Artifacts generated
     * @throws ApiError
     */
    public static generateArtifacts(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: GenerateArtifactsRequest,
    ): CancelablePromise<GenerateArtifactsResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/generate',
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
     * Regenerate artifacts with a change diff
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns RegenerateDiffResponse Differential regeneration result
     * @throws ApiError
     */
    public static generateDiff(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: RegenerateDiffRequest,
    ): CancelablePromise<RegenerateDiffResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/generate/diff',
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Generate Kernel ProductUnitIntent from saved project state
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns KernelProductUnitIntentResponse Kernel ProductUnitIntent generated and validated
     * @throws ApiError
     */
    public static generateProductUnitIntent(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: KernelProductUnitIntentRequest,
    ): CancelablePromise<KernelProductUnitIntentResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/generate/product-unit-intent',
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request â€” invalid or missing parameters`,
            },
        });
    }
    /**
     * Apply a reviewed generated artifact run
     * @param runId
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns GenerateReviewDecisionResponse Generation review decision recorded
     * @throws ApiError
     */
    public static applyGenerationRun(
        runId: string,
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: GenerateReviewDecisionRequest,
    ): CancelablePromise<GenerateReviewDecisionResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/generate/runs/{runId}/apply',
            path: {
                'runId': runId,
            },
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
     * Reject a reviewed generated artifact run
     * @param runId
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns GenerateReviewDecisionResponse Generation review decision recorded
     * @throws ApiError
     */
    public static rejectGenerationRun(
        runId: string,
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: GenerateReviewDecisionRequest,
    ): CancelablePromise<GenerateReviewDecisionResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/generate/runs/{runId}/reject',
            path: {
                'runId': runId,
            },
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
     * Roll back a reviewed generated artifact run
     * @param runId
     * @param requestBody
     * @returns GenerateReviewDecisionResponse Generation review decision recorded
     * @throws ApiError
     */
    public static rollbackGenerationRun(
        runId: string,
        requestBody: GenerateReviewDecisionRequest,
    ): CancelablePromise<GenerateReviewDecisionResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/generate/runs/{runId}/rollback',
            path: {
                'runId': runId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Get generated artifacts for a run
     * @param id
     * @returns any Artifact manifest
     * @throws ApiError
     */
    public static getArtifacts(
        id: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/yappc/generate/artifacts/{id}',
            path: {
                'id': id,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
}
