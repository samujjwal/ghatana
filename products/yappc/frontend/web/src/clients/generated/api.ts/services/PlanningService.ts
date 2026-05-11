/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BacklogItem } from '../models/BacklogItem';
import type { CreateSprintRequest } from '../models/CreateSprintRequest';
import type { MoveItemRequest } from '../models/MoveItemRequest';
import type { Sprint } from '../models/Sprint';
import type { UpdateSprintRequest } from '../models/UpdateSprintRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class PlanningService {
    /**
     * List sprints for a project
     * @param projectId
     * @returns Sprint List of sprints
     * @throws ApiError
     */
    public static listSprints(
        projectId: string,
    ): CancelablePromise<Array<Sprint>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/planning/{projectId}/sprints',
            path: {
                'projectId': projectId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Create a sprint for a project
     * @param projectId
     * @param requestBody
     * @returns Sprint Sprint created
     * @throws ApiError
     */
    public static createSprint(
        projectId: string,
        requestBody: CreateSprintRequest,
    ): CancelablePromise<Sprint> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/planning/{projectId}/sprints',
            path: {
                'projectId': projectId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Get a single sprint
     * @param projectId
     * @param sprintId
     * @returns Sprint Sprint detail
     * @throws ApiError
     */
    public static getSprint(
        projectId: string,
        sprintId: string,
    ): CancelablePromise<Sprint> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/planning/{projectId}/sprints/{sprintId}',
            path: {
                'projectId': projectId,
                'sprintId': sprintId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Update a sprint (name, dates, status)
     * @param projectId
     * @param sprintId
     * @param requestBody
     * @returns Sprint Sprint updated
     * @throws ApiError
     */
    public static updateSprint(
        projectId: string,
        sprintId: string,
        requestBody: UpdateSprintRequest,
    ): CancelablePromise<Sprint> {
        return __request(OpenAPI, {
            method: 'PATCH',
            url: '/planning/{projectId}/sprints/{sprintId}',
            path: {
                'projectId': projectId,
                'sprintId': sprintId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                404: `Resource not found`,
            },
        });
    }
    /**
     * Get the product backlog for a project
     * @param projectId
     * @returns BacklogItem Backlog items
     * @throws ApiError
     */
    public static getProjectBacklog(
        projectId: string,
    ): CancelablePromise<Array<BacklogItem>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/planning/{projectId}/backlog',
            path: {
                'projectId': projectId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Move a backlog item to a sprint or back to the backlog
     * @param projectId
     * @param requestBody
     * @returns any Item moved
     * @throws ApiError
     */
    public static moveBacklogItem(
        projectId: string,
        requestBody: MoveItemRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'PATCH',
            url: '/planning/{projectId}/items/move',
            path: {
                'projectId': projectId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                404: `Resource not found`,
            },
        });
    }
}
