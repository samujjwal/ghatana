/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type ExecutionPhaseResult = {
    executionId: string;
    phase: string;
    status: ExecutionPhaseResult.status;
    output?: Record<string, any>;
};
export namespace ExecutionPhaseResult {
    export enum status {
        PENDING = 'pending',
        RUNNING = 'running',
        SUCCESS = 'success',
        FAILED = 'failed',
        CANCELLED = 'cancelled',
    }
}

