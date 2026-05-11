/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CreatePreviewSessionRequest } from '../models/CreatePreviewSessionRequest';
import type { CreatePreviewSessionResponse } from '../models/CreatePreviewSessionResponse';
import type { ValidatePreviewSessionRequest } from '../models/ValidatePreviewSessionRequest';
import type { ValidatePreviewSessionResponse } from '../models/ValidatePreviewSessionResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class PreviewService {
    /**
     * Create a preview session for artifact inspection
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns CreatePreviewSessionResponse Preview session created
     * @throws ApiError
     */
    public static createPreviewSession(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: CreatePreviewSessionRequest,
    ): CancelablePromise<CreatePreviewSessionResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/preview/session/create',
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
     * Validate a preview session token
     * @param requestBody
     * @returns ValidatePreviewSessionResponse Session validation result
     * @throws ApiError
     */
    public static validatePreviewSession(
        requestBody: ValidatePreviewSessionRequest,
    ): CancelablePromise<ValidatePreviewSessionResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/preview/session/validate',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
}
