/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ValidateRequest } from '../models/ValidateRequest';
import type { ValidateWithConfigRequest } from '../models/ValidateWithConfigRequest';
import type { ValidateWithPolicyRequest } from '../models/ValidateWithPolicyRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ValidateService {
    /**
     * Run default validation suite
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Validation result
     * @throws ApiError
     */
    public static validateArtifacts(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: ValidateRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/validate',
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
     * Run validation with a custom config
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Validation result
     * @throws ApiError
     */
    public static validateWithConfig(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: ValidateWithConfigRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/validate/with-config',
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
     * Run validation against a named policy
     * @param requestBody
     * @returns any Validation result
     * @throws ApiError
     */
    public static validateWithPolicy(
        requestBody: ValidateWithPolicyRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/validate/with-policy',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
