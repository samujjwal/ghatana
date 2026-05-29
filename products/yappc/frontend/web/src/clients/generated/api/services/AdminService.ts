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
    public static listAdminObservabilityReleaseGates(): CancelablePromise<{
        status: 'healthy' | 'degraded' | 'down';
        items: Array<{
            id: string;
            label: string;
            category: 'SLO' | 'Cost' | 'Domain' | 'API';
            status: 'healthy' | 'degraded' | 'down';
            evidenceHref: string;
            refreshedAt: string;
            summary: string;
            source?: string;
            evidence?: Record<string, any>;
        }>;
    }> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/admin/observability/release-gates',
            errors: {
                403: `Permission denied`,
                503: `Release-gate evidence unavailable`,
            },
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
    ): CancelablePromise<Array<Record<string, any>>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/admin/feature-flags',
            query: {
                'tenantId': tenantId,
            },
            errors: {
                403: `Permission denied`,
                503: `Feature flag service unavailable`,
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
        requestBody: {
            enabled: boolean;
            reason: string;
            tenantId?: string;
            description?: string;
            rolloutPercentage?: number;
        },
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/api/admin/feature-flags/{flagKey}',
            path: {
                'flagKey': flagKey,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                403: `Permission denied`,
                503: `Feature flag service unavailable`,
            },
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
    ): CancelablePromise<Array<Record<string, any>>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/admin/feature-flags/{flagKey}/audit',
            path: {
                'flagKey': flagKey,
            },
            query: {
                'tenantId': tenantId,
            },
            errors: {
                403: `Permission denied`,
                503: `Feature flag service unavailable`,
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
    ): CancelablePromise<{
        items?: Array<Record<string, any>>;
        total?: number;
    }> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/admin/ab-experiments',
            query: {
                'status': status,
            },
            errors: {
                403: `Permission denied`,
                503: `A/B testing service unavailable`,
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
        requestBody: {
            experimentName: string;
            description?: string;
            promptName: string;
            promptVersion?: string;
            variantA?: string;
            variantB?: string;
        },
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/admin/ab-experiments',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                403: `Permission denied`,
                503: `A/B testing service unavailable`,
            },
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
        requestBody: {
            variantId: string;
            reason: string;
        },
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/admin/ab-experiments/{experimentId}/promote',
            path: {
                'experimentId': experimentId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                403: `Permission denied`,
                404: `Resource not found`,
                503: `A/B testing service unavailable`,
            },
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
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/admin/ab-experiments/{experimentId}/pause',
            path: {
                'experimentId': experimentId,
            },
            errors: {
                403: `Permission denied`,
                404: `Resource not found`,
                503: `A/B testing service unavailable`,
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
    ): CancelablePromise<{
        items?: Array<Record<string, any>>;
        total?: number;
    }> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/admin/prompt-versions',
            query: {
                'promptName': promptName,
            },
            errors: {
                403: `Permission denied`,
                503: `Prompt version service unavailable`,
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
        requestBody: {
            reason: string;
        },
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/admin/prompt-versions/{versionId}/rollback',
            path: {
                'versionId': versionId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                403: `Permission denied`,
                404: `Resource not found`,
                503: `Prompt version service unavailable`,
            },
        });
    }
    /**
     * Update prompt version weights
     * @param requestBody
     * @returns any Prompt weights updated and rebalanced
     * @throws ApiError
     */
    public static updateAdminPromptVersionWeights(
        requestBody: {
            weights: Record<string, number>;
        },
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'PATCH',
            url: '/api/admin/prompt-versions/weights',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                403: `Permission denied`,
                503: `Prompt version service unavailable`,
            },
        });
    }
}
