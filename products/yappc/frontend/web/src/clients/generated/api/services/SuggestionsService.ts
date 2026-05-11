/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { Suggestion } from '../models/Suggestion';
import type { SuggestionRequest } from '../models/SuggestionRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class SuggestionsService {
    /**
     * Generate lifecycle recommendations
     * Calls the recommendation service with the current project phase and context,
     * returning up to 5 typed suggestions. Internal model routing and provenance
     * remain available in the response lineage where applicable.
     *
     * @param projectId
     * @param requestBody
     * @returns Suggestion Lifecycle recommendations
     * @throws ApiError
     */
    public static generateSuggestions(
        projectId: string,
        requestBody: SuggestionRequest,
    ): CancelablePromise<Array<Suggestion>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/projects/{projectId}/suggestions',
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
     * Dismiss a suggestion
     * @param projectId
     * @param suggestionId
     * @returns void
     * @throws ApiError
     */
    public static dismissSuggestion(
        projectId: string,
        suggestionId: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/api/v1/projects/{projectId}/suggestions/{suggestionId}',
            path: {
                'projectId': projectId,
                'suggestionId': suggestionId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Return rule-based artifact suggestions for the current lifecycle phase
     * @param requestBody
     * @returns any Suggested artifacts for missing phase items
     * @throws ApiError
     */
    public static suggestArtifacts(
        requestBody: {
            context: {
                projectId: string;
                currentPhase: 'INTENT' | 'SHAPE' | 'VALIDATE' | 'GENERATE' | 'RUN' | 'OBSERVE' | 'IMPROVE';
                existingArtifacts: Array<{
                    kind: string;
                    payload?: Record<string, any>;
                }>;
                projectDescription?: string;
            };
            targetKinds?: Array<string>;
        },
    ): CancelablePromise<{
        suggestions: Array<{
            id: string;
            kind: string;
            title: string;
            summary: string;
            reasoning: string;
            confidence: number;
            confidenceType: 'rule_based_heuristic';
            confidenceReason: string;
            suggestedPayload: Record<string, any>;
        }>;
        correlationId: string;
    }> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/ai/suggest-artifacts',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Invalid request payload`,
            },
        });
    }
}
