/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { DeriveShapeRequest } from '../models/DeriveShapeRequest';
import type { GenerateModelRequest } from '../models/GenerateModelRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ShapeService {
    /**
     * Derive system shape from intent
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Shape derived
     * @throws ApiError
     */
    public static deriveShape(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: DeriveShapeRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/shape/derive',
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
     * Generate system model (domain entities, APIs)
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any System model generated
     * @throws ApiError
     */
    public static modelShape(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: GenerateModelRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/shape/model',
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
     * Get a shape record by ID
     * @param id
     * @returns any Shape record
     * @throws ApiError
     */
    public static getShape(
        id: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/yappc/shape/{id}',
            path: {
                'id': id,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
}
