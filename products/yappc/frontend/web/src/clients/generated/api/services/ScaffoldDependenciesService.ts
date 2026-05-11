/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ScaffoldDependenciesService {
    /**
     * Analyze dependencies for a pack
     * @param name
     * @returns any Dependency analysis
     * @throws ApiError
     */
    public static analyzePackDependencies(
        name: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/dependencies/analyze/pack/{name}',
            path: {
                'name': name,
            },
        });
    }
    /**
     * Analyze dependencies for a project
     * @param requestBody
     * @returns any Dependency analysis
     * @throws ApiError
     */
    public static analyzeProjectDependencies(
        requestBody: Record<string, any>,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/dependencies/analyze/project',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Check for dependency conflicts
     * @param requestBody
     * @returns any Conflict report
     * @throws ApiError
     */
    public static detectDependencyConflicts(
        requestBody: Record<string, any>,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/dependencies/conflicts',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Check for conflicts introduced by adding new dependencies
     * @param requestBody
     * @returns any Add-conflict report
     * @throws ApiError
     */
    public static addDependencyConflicts(
        requestBody: Record<string, any>,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/dependencies/add-conflicts',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
