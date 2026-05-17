/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ObservationRequest } from '../models/ObservationRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ObserveService {
    /**
     * Collect an observation for a run
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Observation recorded
     * @throws ApiError
     */
    public static observeRun(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: ObservationRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/observe',
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
     * Query anomalies by tenant and date range
     * @param requestBody
     * @returns any Anomaly query result
     * @throws ApiError
     */
    public static queryAnomalies(
        requestBody: Record<string, any>,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/anomalies',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Create investigation for anomaly
     * @param anomalyId
     * @param requestBody
     * @returns any Investigation created
     * @throws ApiError
     */
    public static createAnomalyInvestigation(
        anomalyId: string,
        requestBody: Record<string, any>,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/anomalies/{anomalyId}/investigation',
            path: {
                'anomalyId': anomalyId,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Report frontend client error payload
     * @param requestBody
     * @returns any Error report accepted
     * @throws ApiError
     */
    public static reportFrontendErrorEvent(
        requestBody: Record<string, any>,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/errors',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
