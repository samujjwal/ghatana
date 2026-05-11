/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AddFeatureRequest } from '../models/AddFeatureRequest';
import type { ScaffoldProjectRequest } from '../models/ScaffoldProjectRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ScaffoldProjectsService {
    /**
     * Create a new scaffold project
     * @param requestBody
     * @returns any Project created
     * @throws ApiError
     */
    public static createScaffoldProject(
        requestBody: ScaffoldProjectRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/scaffold/projects',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Add a feature to an existing scaffold project
     * @param requestBody
     * @returns any Feature added
     * @throws ApiError
     */
    public static addFeatureToProject(
        requestBody: AddFeatureRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/scaffold/projects/add-feature',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Update scaffold project configuration
     * @param requestBody
     * @returns any Project updated
     * @throws ApiError
     */
    public static updateScaffoldProject(
        requestBody: ScaffoldProjectRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/scaffold/projects/update',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Get current scaffold project info
     * @returns any Project info
     * @throws ApiError
     */
    public static getScaffoldProjectInfo(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/scaffold/projects/info',
        });
    }
    /**
     * Get full scaffold project state
     * @returns any Project state
     * @throws ApiError
     */
    public static getScaffoldProjectState(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/scaffold/projects/state',
        });
    }
    /**
     * Validate the current scaffold project
     * @returns any Validation result
     * @throws ApiError
     */
    public static validateScaffoldProject(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/scaffold/projects/validate',
        });
    }
    /**
     * Check for available scaffold pack updates
     * @returns any Update availability
     * @throws ApiError
     */
    public static checkScaffoldUpdates(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/scaffold/projects/check-updates',
        });
    }
    /**
     * Preview changes from applying a scaffold update
     * @param requestBody
     * @returns any Preview diff
     * @throws ApiError
     */
    public static previewScaffoldUpdate(
        requestBody: Record<string, any>,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/scaffold/projects/preview-update',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * List features in the current scaffold project
     * @returns any Feature list
     * @throws ApiError
     */
    public static getScaffoldFeatures(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/scaffold/projects/features',
        });
    }
    /**
     * Export scaffold project state
     * @returns any Exported state
     * @throws ApiError
     */
    public static exportScaffoldProject(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/scaffold/projects/export',
        });
    }
    /**
     * Import scaffold project state
     * @param requestBody
     * @returns any Import result
     * @throws ApiError
     */
    public static importScaffoldProject(
        requestBody: Record<string, any>,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/scaffold/projects/import',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
