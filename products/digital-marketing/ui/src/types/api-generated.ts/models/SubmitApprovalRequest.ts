/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ApprovalTargetType } from './ApprovalTargetType';
export type SubmitApprovalRequest = {
    targetType: ApprovalTargetType;
    targetId: string;
    description: string;
    riskLevel?: number;
    requiredApproverRole?: string;
    validationResultId?: string;
};

