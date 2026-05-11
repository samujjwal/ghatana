/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type AdvancePhaseRequest = {
    projectId: string;
    tenantId?: string;
    targetPhase: AdvancePhaseRequest.targetPhase;
    /**
     * Gate condition verdicts keyed by criterion ID
     */
    conditions?: Record<string, boolean>;
};
export namespace AdvancePhaseRequest {
    export enum targetPhase {
        INTENT = 'INTENT',
        SHAPE = 'SHAPE',
        VALIDATE = 'VALIDATE',
        GENERATE = 'GENERATE',
        RUN = 'RUN',
        OBSERVE = 'OBSERVE',
        LEARN = 'LEARN',
        EVOLVE = 'EVOLVE',
    }
}

