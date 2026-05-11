/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type GenerateReviewDecisionResponse = {
    runId: string;
    projectId: string;
    decision: GenerateReviewDecisionResponse.decision;
    status: GenerateReviewDecisionResponse.status;
    reviewRequired: boolean;
    actorId: string;
    decidedAt: string;
    auditEvent: string;
    message: string;
};
export namespace GenerateReviewDecisionResponse {
    export enum decision {
        APPLY = 'apply',
        REJECT = 'reject',
        ROLLBACK = 'rollback',
    }
    export enum status {
        APPLIED = 'APPLIED',
        REJECTED = 'REJECTED',
        ROLLED_BACK = 'ROLLED_BACK',
    }
}

