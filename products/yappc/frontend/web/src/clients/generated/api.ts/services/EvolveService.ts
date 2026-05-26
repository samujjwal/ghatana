/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { EvolveRequest } from '../models/EvolveRequest';
import type { EvolveWithConstraintsRequest } from '../models/EvolveWithConstraintsRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class EvolveService {
    /**
     * Propose evolutions based on accumulated learning signals
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Evolution proposals
     * @throws ApiError
     */
    public static evolveSystem(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: EvolveRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/evolve',
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Propose evolutions within explicit architectural constraints
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Constrained evolution proposals
     * @throws ApiError
     */
    public static evolveWithConstraints(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: EvolveWithConstraintsRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/evolve/with-constraints',
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Approve an evolution proposal
     * @param proposalId
     * @param requestBody
     * @returns any Evolution proposal approved
     * @throws ApiError
     */
    public static approveEvolutionProposal(
        proposalId: string,
        requestBody: Record<string, any>,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/evolve/{proposalId}/approve',
            path: {
                'proposalId': proposalId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request â€” invalid or missing parameters`,
            },
        });
    }
    /**
     * Reject an evolution proposal
     * @param proposalId
     * @param requestBody
     * @returns any Evolution proposal rejected
     * @throws ApiError
     */
    public static rejectEvolutionProposal(
        proposalId: string,
        requestBody: Record<string, any>,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/evolve/{proposalId}/reject',
            path: {
                'proposalId': proposalId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request â€” invalid or missing parameters`,
            },
        });
    }
}
