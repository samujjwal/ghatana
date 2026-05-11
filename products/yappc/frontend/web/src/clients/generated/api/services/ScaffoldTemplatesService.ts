/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { TemplateRenderRequest } from '../models/TemplateRenderRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ScaffoldTemplatesService {
    /**
     * Render a template with provided variables
     * @param requestBody
     * @returns any Rendered output
     * @throws ApiError
     */
    public static renderTemplate(
        requestBody: TemplateRenderRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/templates/render',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * List available template helper functions
     * @returns any Helper list
     * @throws ApiError
     */
    public static getTemplateHelpers(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/templates/helpers',
        });
    }
    /**
     * Validate a template definition
     * @param requestBody
     * @returns any Validation result
     * @throws ApiError
     */
    public static validateTemplate(
        requestBody: Record<string, any>,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/templates/validate',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
