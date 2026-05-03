/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ApprovalRecordResponse } from '../models/ApprovalRecordResponse';
import type { DecideApprovalRequest } from '../models/DecideApprovalRequest';
import type { PendingApprovalsResponse } from '../models/PendingApprovalsResponse';
import type { SubmitApprovalRequest } from '../models/SubmitApprovalRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ApprovalsService {
    /**
     * List pending approvals
     * @returns PendingApprovalsResponse List of pending approvals
     * @throws ApiError
     */
    public static listPendingApprovals(): CancelablePromise<PendingApprovalsResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/approvals/pending',
        });
    }
    /**
     * Get approval by request ID
     * @param requestId
     * @returns ApprovalRecordResponse Approval record
     * @throws ApiError
     */
    public static getApproval(
        requestId: string,
    ): CancelablePromise<ApprovalRecordResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/approvals/{requestId}',
            path: {
                'requestId': requestId,
            },
        });
    }
    /**
     * Submit for approval
     * @param requestBody
     * @returns ApprovalRecordResponse Approval submitted
     * @throws ApiError
     */
    public static submitApproval(
        requestBody: SubmitApprovalRequest,
    ): CancelablePromise<ApprovalRecordResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/approvals',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Decide on approval
     * @param requestId
     * @param requestBody
     * @returns ApprovalRecordResponse Decision recorded
     * @throws ApiError
     */
    public static decideApproval(
        requestId: string,
        requestBody: DecideApprovalRequest,
    ): CancelablePromise<ApprovalRecordResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/approvals/{requestId}/decide',
            path: {
                'requestId': requestId,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
