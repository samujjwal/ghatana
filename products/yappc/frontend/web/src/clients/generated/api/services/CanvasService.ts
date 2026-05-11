/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { Canvas } from '../models/Canvas';
import type { CanvasPayload } from '../models/CanvasPayload';
import type { CanvasVersion } from '../models/CanvasVersion';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class CanvasService {
    /**
     * List all canvases accessible to the requester
     * @returns Canvas Canvas list
     * @throws ApiError
     */
    public static listCanvas(): CancelablePromise<Array<Canvas>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/canvas',
        });
    }
    /**
     * Get a canvas by project and optional canvas ID
     * @param projectId
     * @param canvasId
     * @returns Canvas Canvas detail
     * @throws ApiError
     */
    public static getCanvas(
        projectId: string,
        canvasId?: string,
    ): CancelablePromise<Canvas> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/canvas/{projectId}/{canvasId}',
            path: {
                'projectId': projectId,
                'canvasId': canvasId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Get the canvas for a project
     * @param projectId
     * @returns Canvas Canvas
     * @throws ApiError
     */
    public static getProjectCanvas(
        projectId: string,
    ): CancelablePromise<Canvas> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/projects/{projectId}/canvas',
            path: {
                'projectId': projectId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Save (upsert) the canvas for a project
     * @param projectId
     * @param requestBody
     * @returns Canvas Canvas saved
     * @throws ApiError
     */
    public static saveProjectCanvas(
        projectId: string,
        requestBody: CanvasPayload,
    ): CancelablePromise<Canvas> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/projects/{projectId}/canvas',
            path: {
                'projectId': projectId,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * List historical versions of a project canvas
     * @param projectId
     * @returns CanvasVersion Canvas version list
     * @throws ApiError
     */
    public static listCanvasVersions(
        projectId: string,
    ): CancelablePromise<Array<CanvasVersion>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/projects/{projectId}/canvas/versions',
            path: {
                'projectId': projectId,
            },
        });
    }
    /**
     * Restore a canvas to a previous version
     * @param projectId
     * @param requestBody
     * @returns Canvas Canvas restored
     * @throws ApiError
     */
    public static restoreCanvasVersion(
        projectId: string,
        requestBody: {
            versionId: string;
        },
    ): CancelablePromise<Canvas> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/projects/{projectId}/canvas/restore',
            path: {
                'projectId': projectId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                404: `Resource not found`,
            },
        });
    }
}
