/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class AdminService {
    /**
     * List admin release-gate observability evidence
     * @returns any Release-gate evidence summary for SLO, cost, domain, and OpenAPI gates
     * @throws ApiError
     */
    public static listAdminObservabilityReleaseGates(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/admin/observability/release-gates',
        });
    }
    /**
     * List tenant feature flags
     * @param tenantId
     * @returns any Tenant feature flags
     * @throws ApiError
     */
    public static listAdminFeatureFlags(
        tenantId?: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/admin/feature-flags',
            query: {
                'tenantId': tenantId,
            },
        });
    }
    /**
     * Set tenant feature flag
     * @param flagKey
     * @param requestBody
     * @returns any Updated feature flag
     * @throws ApiError
     */
    public static setAdminFeatureFlag(
        flagKey: string,
        requestBody: Record<string, any>,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/api/admin/feature-flags/{flagKey}',
            path: {
                'flagKey': flagKey,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * List tenant feature flag audit log
     * @param flagKey
     * @param tenantId
     * @returns any Feature flag audit entries
     * @throws ApiError
     */
    public static listAdminFeatureFlagAudit(
        flagKey: string,
        tenantId?: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/admin/feature-flags/{flagKey}/audit',
            path: {
                'flagKey': flagKey,
            },
            query: {
                'tenantId': tenantId,
            },
        });
    }
    /**
     * List prompt/model A/B experiments
     * @param status
     * @returns any A/B experiment list
     * @throws ApiError
     */
    public static listAdminAbExperiments(
        status?: 'running' | 'completed' | 'paused',
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/admin/ab-experiments',
            query: {
                'status': status,
            },
        });
    }
    /**
     * Create prompt/model A/B experiment
     * @param requestBody
     * @returns any Created A/B experiment
     * @throws ApiError
     */
    public static createAdminAbExperiment(
        requestBody: Record<string, any>,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/admin/ab-experiments',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Promote A/B experiment winner
     * @param experimentId
     * @param requestBody
     * @returns any Promoted A/B experiment with winner, evaluation, and rollback metadata
     * @throws ApiError
     */
    public static promoteAdminAbExperimentWinner(
        experimentId: string,
        requestBody: Record<string, any>,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/admin/ab-experiments/{experimentId}/promote',
            path: {
                'experimentId': experimentId,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Pause A/B experiment
     * @param experimentId
     * @returns any Paused A/B experiment
     * @throws ApiError
     */
    public static pauseAdminAbExperiment(
        experimentId: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/admin/ab-experiments/{experimentId}/pause',
            path: {
                'experimentId': experimentId,
            },
        });
    }
    /**
     * List prompt versions
     * @param promptName
     * @returns any Prompt version list
     * @throws ApiError
     */
    public static listAdminPromptVersions(
        promptName?: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/admin/prompt-versions',
            query: {
                'promptName': promptName,
            },
        });
    }
    /**
     * Roll back to prompt version
     * @param versionId
     * @param requestBody
     * @returns any Rolled back prompt version
     * @throws ApiError
     */
    public static rollbackAdminPromptVersion(
        versionId: string,
        requestBody: Record<string, any>,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/admin/prompt-versions/{versionId}/rollback',
            path: {
                'versionId': versionId,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Update prompt version weights
     * @param requestBody
     * @returns any Prompt weights updated and rebalanced
     * @throws ApiError
     */
    public static updateAdminPromptVersionWeights(
        requestBody: Record<string, any>,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'PATCH',
            url: '/api/admin/prompt-versions/weights',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
