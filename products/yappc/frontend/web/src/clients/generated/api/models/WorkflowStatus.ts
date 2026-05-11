/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type WorkflowStatus = {
    runId?: string;
    templateId?: string;
    status?: WorkflowStatus.status;
    startedAt?: string;
    completedAt?: string | null;
    error?: string | null;
};
export namespace WorkflowStatus {
    export enum status {
        PENDING = 'PENDING',
        RUNNING = 'RUNNING',
        COMPLETED = 'COMPLETED',
        FAILED = 'FAILED',
        CANCELLED = 'CANCELLED',
    }
}

