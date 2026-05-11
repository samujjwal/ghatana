/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type ExecutionResult = {
    id: string;
    projectId: string;
    phase: string;
    status: ExecutionResult.status;
    startedAt: string;
    completedAt?: string | null;
};
export namespace ExecutionResult {
    export enum status {
        PENDING = 'pending',
        RUNNING = 'running',
        SUCCESS = 'success',
        FAILED = 'failed',
        CANCELLED = 'cancelled',
    }
}

