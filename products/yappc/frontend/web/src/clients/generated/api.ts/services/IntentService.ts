/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AnalyzeIntentRequest } from '../models/AnalyzeIntentRequest';
import type { CaptureIntentRequest } from '../models/CaptureIntentRequest';
import type { IntentResponse } from '../models/IntentResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class IntentService {
    /**
     * Capture product intent
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns IntentResponse Intent captured
     * @throws ApiError
     */
    public static captureIntent(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: CaptureIntentRequest,
    ): CancelablePromise<IntentResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/intent/capture',
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
     * Analyze captured intent
     * Sends the captured intent through the guided enrichment pipeline for decomposition and review.
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Intent analysis result
     * @throws ApiError
     */
    public static analyzeIntent(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: AnalyzeIntentRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/intent/analyze',
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
     * Get a captured intent by ID
     * @param id
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @returns any Intent record
     * @throws ApiError
     */
    public static getIntent(
        id: string,
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/yappc/intent/{id}',
            path: {
                'id': id,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
}
