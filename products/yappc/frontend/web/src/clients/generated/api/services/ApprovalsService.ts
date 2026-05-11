/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ApprovalDecisionRequest } from '../models/ApprovalDecisionRequest';
import type { ApprovalRequest } from '../models/ApprovalRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ApprovalsService {
    /**
     * List pending approvals for the authenticated user
     * @param xTenantId Tenant scoping header
     * @returns ApprovalRequest List of pending approval requests
     * @throws ApiError
     */
    public static listPendingApprovals(
        xTenantId: string,
    ): CancelablePromise<Array<ApprovalRequest>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/approvals/pending',
            headers: {
                'X-Tenant-Id': xTenantId,
            },
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Approve an approval request
     * @param id
     * @param xTenantId
     * @param requestBody
     * @returns ApprovalRequest Approval recorded
     * @throws ApiError
     */
    public static approveRequest(
        id: string,
        xTenantId: string,
        requestBody?: ApprovalDecisionRequest,
    ): CancelablePromise<ApprovalRequest> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/approvals/{id}/approve',
            path: {
                'id': id,
            },
            headers: {
                'X-Tenant-Id': xTenantId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                404: `Resource not found`,
                409: `Approval already decided`,
            },
        });
    }
    /**
     * Reject an approval request
     * @param id
     * @param xTenantId
     * @param requestBody
     * @returns ApprovalRequest Rejection recorded
     * @throws ApiError
     */
    public static rejectRequest(
        id: string,
        xTenantId: string,
        requestBody?: ApprovalDecisionRequest,
    ): CancelablePromise<ApprovalRequest> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/approvals/{id}/reject',
            path: {
                'id': id,
            },
            headers: {
                'X-Tenant-Id': xTenantId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                404: `Resource not found`,
            },
        });
    }
}
