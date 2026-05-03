/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ApprovalTargetType } from './ApprovalTargetType';
export type ApprovalSnapshot = {
    requestId: string;
    targetType: ApprovalTargetType;
    targetId: string;
    targetWorkspaceId: string;
    snapshotSummary: string;
    validationResultId: string | null;
    riskLevel: number;
    requiredApproverRole: string;
    snapshotAt: string;
};

