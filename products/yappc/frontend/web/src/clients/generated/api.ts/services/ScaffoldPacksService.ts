/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ScaffoldPacksService {
    /**
     * List all available scaffold packs
     * @returns any Pack list
     * @throws ApiError
     */
    public static listPacks(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/packs',
        });
    }
    /**
     * List supported programming languages
     * @returns any Language list
     * @throws ApiError
     */
    public static getPackLanguages(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/packs/languages',
        });
    }
    /**
     * List pack categories
     * @returns any Category list
     * @throws ApiError
     */
    public static getPackCategories(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/packs/categories',
        });
    }
    /**
     * List supported target platforms
     * @returns any Platform list
     * @throws ApiError
     */
    public static getPackPlatforms(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/packs/platforms',
        });
    }
    /**
     * Refresh the pack registry from remote sources
     * @returns any Refresh result
     * @throws ApiError
     */
    public static refreshPacks(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/packs/refresh',
        });
    }
    /**
     * Get pack descriptor by name
     * @param name
     * @returns any Pack descriptor
     * @throws ApiError
     */
    public static getPack(
        name: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/packs/{name}',
            path: {
                'name': name,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Validate a pack's integrity and schema
     * @param name
     * @returns any Validation result
     * @throws ApiError
     */
    public static validatePack(
        name: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/packs/{name}/validate',
            path: {
                'name': name,
            },
        });
    }
    /**
     * List templates provided by a pack
     * @param name
     * @returns any Template list
     * @throws ApiError
     */
    public static getPackTemplates(
        name: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/packs/{name}/templates',
            path: {
                'name': name,
            },
        });
    }
    /**
     * Get variable schema for a pack
     * @param name
     * @returns any Variable schema
     * @throws ApiError
     */
    public static getPackVariables(
        name: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/packs/{name}/variables',
            path: {
                'name': name,
            },
        });
    }
}
