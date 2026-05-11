/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type ImportReviewDecisionResponse = {
    artifactId: string;
    reviewItemId: string;
    kind: ImportReviewDecisionResponse.kind;
    decision: ImportReviewDecisionResponse.decision;
    auditRecordId: string;
    auditRecorded: boolean;
    reviewedAt: string;
};
export namespace ImportReviewDecisionResponse {
    export enum kind {
        LOSS_POINT = 'loss-point',
        RESIDUAL_ISLAND = 'residual-island',
    }
    export enum decision {
        APPLIED = 'applied',
        SKIPPED = 'skipped',
        PROMOTED = 'promoted',
    }
}

