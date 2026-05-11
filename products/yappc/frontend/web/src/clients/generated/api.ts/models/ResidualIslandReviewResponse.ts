/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type ResidualIslandReviewResponse = {
    artifactId: string;
    residualIslandId: string;
    decision: ResidualIslandReviewResponse.decision;
    auditRecordId: string;
    auditRecorded: boolean;
    reviewedAt: string;
};
export namespace ResidualIslandReviewResponse {
    export enum decision {
        ACCEPTED = 'ACCEPTED',
        REJECTED = 'REJECTED',
    }
}

