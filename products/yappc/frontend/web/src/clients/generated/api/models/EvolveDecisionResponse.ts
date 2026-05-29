/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type EvolveDecisionResponse = {
    proposalId: string;
    tenantId: string;
    projectId: string;
    decision: EvolveDecisionResponse.decision;
    shouldExecuteLifecycle: boolean;
    executionPhases: Array<string>;
    productUnitIntentRef: string;
    metadata: Record<string, any>;
};
export namespace EvolveDecisionResponse {
    export enum decision {
        APPROVED = 'APPROVED',
        REJECTED = 'REJECTED',
    }
}

