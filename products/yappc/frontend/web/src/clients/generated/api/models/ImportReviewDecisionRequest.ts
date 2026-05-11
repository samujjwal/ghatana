/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type ImportReviewDecisionRequest = {
    reviewItemId: string;
    kind: ImportReviewDecisionRequest.kind;
    decision: ImportReviewDecisionRequest.decision;
    label?: string;
    details?: string;
    notes?: string;
};
export namespace ImportReviewDecisionRequest {
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

