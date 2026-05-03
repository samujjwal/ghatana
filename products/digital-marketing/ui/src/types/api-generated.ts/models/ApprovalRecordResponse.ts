/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ApprovalStatus } from './ApprovalStatus';
import type { ApprovalTargetType } from './ApprovalTargetType';
export type ApprovalRecordResponse = {
    requestId: string;
    tenantId: string;
    workspaceId: string;
    targetType: ApprovalTargetType | null;
    targetId: string | null;
    description: string | null;
    riskLevel: number;
    requiredApproverRole: string;
    status: ApprovalStatus;
    submittedAt: string;
    submittedBy: string;
    decidedAt?: string | null;
    decidedBy?: string | null;
    comment?: string | null;
    snapshotSummary?: string | null;
    validationResultId?: string | null;
    snapshotAt?: string | null;
};

