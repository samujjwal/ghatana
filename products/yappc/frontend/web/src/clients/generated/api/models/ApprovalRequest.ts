/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type ApprovalRequest = {
    id?: string;
    projectId?: string;
    tenantId?: string;
    requesterId?: string;
    approverId?: string;
    phase?: string;
    status?: ApprovalRequest.status;
    notes?: string;
    createdAt?: string;
    decidedAt?: string | null;
};
export namespace ApprovalRequest {
    export enum status {
        PENDING = 'PENDING',
        APPROVED = 'APPROVED',
        REJECTED = 'REJECTED',
        EXPIRED = 'EXPIRED',
    }
}

