/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { Campaign } from '../models/Campaign';
import type { CampaignListResponse } from '../models/CampaignListResponse';
import type { ConsentRecordRequest } from '../models/ConsentRecordRequest';
import type { ConsentRevokeRequest } from '../models/ConsentRevokeRequest';
import type { CreateCampaignRequest } from '../models/CreateCampaignRequest';
import type { DashboardSummary } from '../models/DashboardSummary';
import type { SuppressionRequest } from '../models/SuppressionRequest';
import type { UnsubscribeRequest } from '../models/UnsubscribeRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class DefaultService {
    /**
     * Get dashboard summary
     * Returns backend-computed dashboard facts with metric source,
     * formula version, freshness, confidence, partial-data state,
     * and tenant/workspace authorization scope.
     *
     * @param workspaceId The workspace scope for dashboard metrics
     * @param xTenantId
     * @param xCorrelationId
     * @returns DashboardSummary Canonical dashboard summary
     * @throws ApiError
     */
    public static getDashboardSummary(
        workspaceId: string,
        xTenantId: string,
        xCorrelationId?: string,
    ): CancelablePromise<DashboardSummary> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/workspaces/{workspaceId}/dashboard',
            path: {
                'workspaceId': workspaceId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Correlation-ID': xCorrelationId,
            },
            errors: {
                403: `Not authorized`,
                500: `Internal server error`,
            },
        });
    }
    /**
     * List campaigns
     * P0-001: Returns a paginated list of campaigns for the workspace.
     * Results are ordered by createdAt descending (newest first).
     *
     * @param workspaceId The workspace scope for the operation
     * @param xTenantId Tenant scope for isolation
     * @param limit Maximum number of results to return
     * @param offset Number of results to skip for pagination
     * @param xCorrelationId Correlation ID for tracing (auto-generated if absent)
     * @returns CampaignListResponse Paginated list of campaigns
     * @throws ApiError
     */
    public static listCampaigns(
        workspaceId: string,
        xTenantId: string,
        limit: number = 20,
        offset?: number,
        xCorrelationId?: string,
    ): CancelablePromise<CampaignListResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/workspaces/{workspaceId}/campaigns',
            path: {
                'workspaceId': workspaceId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Correlation-ID': xCorrelationId,
            },
            query: {
                'limit': limit,
                'offset': offset,
            },
            errors: {
                400: `Bad request - missing required parameters`,
                403: `Forbidden - not authorized to list campaigns`,
                500: `Internal server error`,
            },
        });
    }
    /**
     * Create a campaign
     * Creates a new campaign in DRAFT status. Requires idempotency key.
     *
     * @param workspaceId The workspace scope for the operation
     * @param xTenantId
     * @param xIdempotencyKey Required for write operations
     * @param requestBody
     * @param xCorrelationId
     * @returns Campaign Campaign created successfully
     * @throws ApiError
     */
    public static createCampaign(
        workspaceId: string,
        xTenantId: string,
        xIdempotencyKey: string,
        requestBody: CreateCampaignRequest,
        xCorrelationId?: string,
    ): CancelablePromise<Campaign> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/workspaces/{workspaceId}/campaigns',
            path: {
                'workspaceId': workspaceId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Idempotency-Key': xIdempotencyKey,
                'X-Correlation-ID': xCorrelationId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Invalid request body`,
                403: `Not authorized`,
                409: `Conflict - idempotency key already used`,
                422: `Compliance violation`,
            },
        });
    }
    /**
     * Get funnel analytics
     * Boundary reporting route. Returns 423 until canonical analytics runtime data is available.
     * @param workspaceId
     * @param xTenantId
     * @returns void
     * @throws ApiError
     */
    public static getFunnelAnalytics(
        workspaceId: string,
        xTenantId: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/workspaces/{workspaceId}/funnel-analytics',
            path: {
                'workspaceId': workspaceId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
            },
            errors: {
                423: `Reporting capability is locked`,
            },
        });
    }
    /**
     * Get attribution reporting
     * Boundary reporting route. Returns 423 until attribution source-event lineage is available.
     * @param workspaceId
     * @param xTenantId
     * @returns void
     * @throws ApiError
     */
    public static getAttributionReporting(
        workspaceId: string,
        xTenantId: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/workspaces/{workspaceId}/attribution',
            path: {
                'workspaceId': workspaceId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
            },
            errors: {
                423: `Reporting capability is locked`,
            },
        });
    }
    /**
     * Get ROI/ROAS reporting
     * Boundary reporting route. Returns 423 until cost/revenue source metrics are available.
     * @param workspaceId
     * @param xTenantId
     * @returns void
     * @throws ApiError
     */
    public static getRoiRoasReporting(
        workspaceId: string,
        xTenantId: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/workspaces/{workspaceId}/roi-roas',
            path: {
                'workspaceId': workspaceId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
            },
            errors: {
                423: `Reporting capability is locked`,
            },
        });
    }
    /**
     * Record consent
     * @param workspaceId
     * @param xTenantId
     * @param xIdempotencyKey
     * @param requestBody
     * @returns any Consent recorded
     * @throws ApiError
     */
    public static recordConsent(
        workspaceId: string,
        xTenantId: string,
        xIdempotencyKey: string,
        requestBody: ConsentRecordRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/workspaces/{workspaceId}/consent',
            path: {
                'workspaceId': workspaceId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Idempotency-Key': xIdempotencyKey,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Invalid request`,
                403: `Not authorized`,
            },
        });
    }
    /**
     * Check consent
     * @param workspaceId
     * @param subjectId
     * @param purpose
     * @param xTenantId
     * @returns any Consent check result
     * @throws ApiError
     */
    public static checkConsent(
        workspaceId: string,
        subjectId: string,
        purpose: string,
        xTenantId: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/workspaces/{workspaceId}/consent/check',
            path: {
                'workspaceId': workspaceId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
            },
            query: {
                'subjectId': subjectId,
                'purpose': purpose,
            },
        });
    }
    /**
     * Revoke consent
     * @param workspaceId
     * @param xTenantId
     * @param xIdempotencyKey
     * @param requestBody
     * @returns any Consent revoked
     * @throws ApiError
     */
    public static revokeConsent(
        workspaceId: string,
        xTenantId: string,
        xIdempotencyKey: string,
        requestBody: ConsentRevokeRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/workspaces/{workspaceId}/consent/revoke',
            path: {
                'workspaceId': workspaceId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Idempotency-Key': xIdempotencyKey,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Export consent proof
     * @param workspaceId
     * @param subjectId
     * @param xTenantId
     * @returns any Consent proof history
     * @throws ApiError
     */
    public static exportConsentProof(
        workspaceId: string,
        subjectId: string,
        xTenantId: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/workspaces/{workspaceId}/consent/proof/{subjectId}',
            path: {
                'workspaceId': workspaceId,
                'subjectId': subjectId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
            },
        });
    }
    /**
     * Add suppression
     * @param workspaceId
     * @param xTenantId
     * @param xIdempotencyKey
     * @param requestBody
     * @returns any Suppression recorded
     * @throws ApiError
     */
    public static addSuppression(
        workspaceId: string,
        xTenantId: string,
        xIdempotencyKey: string,
        requestBody: SuppressionRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/workspaces/{workspaceId}/suppression',
            path: {
                'workspaceId': workspaceId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Idempotency-Key': xIdempotencyKey,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Check suppression
     * @param workspaceId
     * @param email
     * @param xTenantId
     * @returns any Suppression check result
     * @throws ApiError
     */
    public static checkSuppression(
        workspaceId: string,
        email: string,
        xTenantId: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/workspaces/{workspaceId}/suppression/check',
            path: {
                'workspaceId': workspaceId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
            },
            query: {
                'email': email,
            },
        });
    }
    /**
     * Unsubscribe and suppress contact point
     * @param workspaceId
     * @param xTenantId
     * @param xIdempotencyKey
     * @param requestBody
     * @returns any Contact point suppressed and consent withdrawn
     * @throws ApiError
     */
    public static unsubscribeContact(
        workspaceId: string,
        xTenantId: string,
        xIdempotencyKey: string,
        requestBody: UnsubscribeRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/workspaces/{workspaceId}/unsubscribe',
            path: {
                'workspaceId': workspaceId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Idempotency-Key': xIdempotencyKey,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Get a campaign
     * Returns a single campaign by ID.
     * @param workspaceId
     * @param campaignId
     * @param xTenantId
     * @param xCorrelationId
     * @returns Campaign Campaign found
     * @throws ApiError
     */
    public static getCampaign(
        workspaceId: string,
        campaignId: string,
        xTenantId: string,
        xCorrelationId?: string,
    ): CancelablePromise<Campaign> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/workspaces/{workspaceId}/campaigns/{campaignId}',
            path: {
                'workspaceId': workspaceId,
                'campaignId': campaignId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Correlation-ID': xCorrelationId,
            },
            errors: {
                404: `Campaign not found`,
            },
        });
    }
    /**
     * Launch a campaign
     * Accepts a launch request after compliance preflight and approval checks.
     * Paid-search campaigns return PENDING_LAUNCH after durable Google Ads command creation,
     * EXTERNAL_EXECUTION_BLOCKED when connector governance blocks execution, or LAUNCH_FAILED
     * when required launch inputs are missing.
     *
     * @param workspaceId
     * @param campaignId
     * @param xTenantId
     * @param xIdempotencyKey
     * @param xCorrelationId
     * @returns Campaign Campaign launched successfully
     * @throws ApiError
     */
    public static launchCampaign(
        workspaceId: string,
        campaignId: string,
        xTenantId: string,
        xIdempotencyKey: string,
        xCorrelationId?: string,
    ): CancelablePromise<Campaign> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/workspaces/{workspaceId}/campaigns/{campaignId}/launch',
            path: {
                'workspaceId': workspaceId,
                'campaignId': campaignId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Idempotency-Key': xIdempotencyKey,
                'X-Correlation-ID': xCorrelationId,
            },
            errors: {
                400: `Invalid request`,
                403: `Not authorized`,
                404: `Campaign not found`,
                409: `Conflict - invalid state transition`,
                422: `Compliance or risk check failed`,
            },
        });
    }
    /**
     * Pause a campaign
     * Transitions a campaign from LAUNCHED to PAUSED status.
     *
     * @param workspaceId
     * @param campaignId
     * @param xTenantId
     * @param xIdempotencyKey
     * @param xCorrelationId
     * @returns Campaign Campaign paused successfully
     * @throws ApiError
     */
    public static pauseCampaign(
        workspaceId: string,
        campaignId: string,
        xTenantId: string,
        xIdempotencyKey: string,
        xCorrelationId?: string,
    ): CancelablePromise<Campaign> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/workspaces/{workspaceId}/campaigns/{campaignId}/pause',
            path: {
                'workspaceId': workspaceId,
                'campaignId': campaignId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Idempotency-Key': xIdempotencyKey,
                'X-Correlation-ID': xCorrelationId,
            },
            errors: {
                403: `Not authorized`,
                404: `Campaign not found`,
                409: `Conflict - invalid state transition`,
            },
        });
    }
}
