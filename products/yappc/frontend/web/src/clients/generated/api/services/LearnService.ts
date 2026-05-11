/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { LearnRequest } from '../models/LearnRequest';
import type { LearnWithContextRequest } from '../models/LearnWithContextRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class LearnService {
    /**
     * Analyze a completed run and record learning signals
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Learning result
     * @throws ApiError
     */
    public static learnFromRun(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: LearnRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/learn',
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
     * Analyze with explicit contextual metadata
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Learning result with context
     * @throws ApiError
     */
    public static learnWithContext(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: LearnWithContextRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/learn/with-context',
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
}
