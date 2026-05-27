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
    /**
     * Caller-supplied retry key for idempotent primary phase action execution
     */
    idempotencyKey?: string;
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

