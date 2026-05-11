/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CodeAssociation } from '../models/CodeAssociation';
import type { CodeAssociationStats } from '../models/CodeAssociationStats';
import type { CreateCodeAssociationRequest } from '../models/CreateCodeAssociationRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class CodeAssociationsService {
    /**
     * List code associations
     * @param artifactId
     * @returns CodeAssociation Code associations
     * @throws ApiError
     */
    public static listCodeAssociations(
        artifactId?: string,
    ): CancelablePromise<Array<CodeAssociation>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/code-associations',
            query: {
                'artifactId': artifactId,
            },
        });
    }
    /**
     * Create a code association
     * @param requestBody
     * @returns CodeAssociation Code association created
     * @throws ApiError
     */
    public static createCodeAssociation(
        requestBody: CreateCodeAssociationRequest,
    ): CancelablePromise<CodeAssociation> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/code-associations',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Get a code association
     * @param associationId
     * @returns CodeAssociation Code association
     * @throws ApiError
     */
    public static getCodeAssociation(
        associationId: string,
    ): CancelablePromise<CodeAssociation> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/code-associations/{associationId}',
            path: {
                'associationId': associationId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Delete a code association
     * @param associationId
     * @returns void
     * @throws ApiError
     */
    public static deleteCodeAssociation(
        associationId: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/api/code-associations/{associationId}',
            path: {
                'associationId': associationId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * List code associations for an artifact
     * @param artifactId
     * @returns CodeAssociation Code associations
     * @throws ApiError
     */
    public static listArtifactCodeAssociations(
        artifactId: string,
    ): CancelablePromise<Array<CodeAssociation>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/artifacts/{artifactId}/code-associations',
            path: {
                'artifactId': artifactId,
            },
        });
    }
    /**
     * Get code association statistics for an artifact
     * @param artifactId
     * @returns CodeAssociationStats Association stats
     * @throws ApiError
     */
    public static getCodeAssociationStats(
        artifactId: string,
    ): CancelablePromise<CodeAssociationStats> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/code-associations/stats/{artifactId}',
            path: {
                'artifactId': artifactId,
            },
        });
    }
}
