/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AiInsight } from '../models/AiInsight';
import type { AnomalyAlert } from '../models/AnomalyAlert';
import type { CreateDevSecOpsItemRequest } from '../models/CreateDevSecOpsItemRequest';
import type { DevSecOpsItem } from '../models/DevSecOpsItem';
import type { DevSecOpsOverview } from '../models/DevSecOpsOverview';
import type { DevSecOpsPhase } from '../models/DevSecOpsPhase';
import type { UpdateDevSecOpsItemRequest } from '../models/UpdateDevSecOpsItemRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class DevSecOpsService {
    /**
     * Get DevSecOps dashboard overview
     * @returns DevSecOpsOverview DevSecOps overview
     * @throws ApiError
     */
    public static getDevSecOpsOverview(): CancelablePromise<DevSecOpsOverview> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/devsecops/overview',
        });
    }
    /**
     * Get DevSecOps recommended insights
     * @returns AiInsight Recommended insights
     * @throws ApiError
     */
    public static getDevSecOpsAiInsights(): CancelablePromise<Array<AiInsight>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/devsecops/ai-insights',
        });
    }
    /**
     * Get active anomaly alerts
     * @returns AnomalyAlert Anomaly alerts
     * @throws ApiError
     */
    public static getDevSecOpsAnomalyAlerts(): CancelablePromise<Array<AnomalyAlert>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/devsecops/anomaly-alerts',
        });
    }
    /**
     * List DevSecOps items (vulnerabilities, findings, etc.)
     * @returns DevSecOpsItem DevSecOps items
     * @throws ApiError
     */
    public static listDevSecOpsItems(): CancelablePromise<Array<DevSecOpsItem>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/devsecops/items',
        });
    }
    /**
     * Create a DevSecOps item
     * @param requestBody
     * @returns DevSecOpsItem Item created
     * @throws ApiError
     */
    public static createDevSecOpsItem(
        requestBody: CreateDevSecOpsItemRequest,
    ): CancelablePromise<DevSecOpsItem> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/devsecops/items',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Bulk update DevSecOps items
     * @param requestBody
     * @returns any Items updated
     * @throws ApiError
     */
    public static bulkUpdateDevSecOpsItems(
        requestBody: {
            ids: Array<string>;
            update: UpdateDevSecOpsItemRequest;
        },
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'PATCH',
            url: '/api/devsecops/items/bulk-update',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Get a DevSecOps item
     * @param id
     * @returns DevSecOpsItem Item detail
     * @throws ApiError
     */
    public static getDevSecOpsItem(
        id: string,
    ): CancelablePromise<DevSecOpsItem> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/devsecops/items/{id}',
            path: {
                'id': id,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Update a DevSecOps item
     * @param id
     * @param requestBody
     * @returns DevSecOpsItem Item updated
     * @throws ApiError
     */
    public static updateDevSecOpsItem(
        id: string,
        requestBody: UpdateDevSecOpsItemRequest,
    ): CancelablePromise<DevSecOpsItem> {
        return __request(OpenAPI, {
            method: 'PATCH',
            url: '/api/devsecops/items/{id}',
            path: {
                'id': id,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Delete a DevSecOps item
     * @param id
     * @returns void
     * @throws ApiError
     */
    public static deleteDevSecOpsItem(
        id: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/api/devsecops/items/{id}',
            path: {
                'id': id,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * List DevSecOps phases
     * @returns DevSecOpsPhase Phases list
     * @throws ApiError
     */
    public static listDevSecOpsPhases(): CancelablePromise<Array<DevSecOpsPhase>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/devsecops/phases',
        });
    }
    /**
     * Get a DevSecOps phase
     * @param phaseId
     * @returns DevSecOpsPhase Phase detail
     * @throws ApiError
     */
    public static getDevSecOpsPhase(
        phaseId: string,
    ): CancelablePromise<DevSecOpsPhase> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/devsecops/phases/{phaseId}',
            path: {
                'phaseId': phaseId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
}
