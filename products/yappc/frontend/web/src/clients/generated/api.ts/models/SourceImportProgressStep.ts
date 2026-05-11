/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type SourceImportProgressStep = {
    id: string;
    label: string;
    status: SourceImportProgressStep.status;
    percent: number;
    message?: string;
    startedAt?: string;
    completedAt?: string;
};
export namespace SourceImportProgressStep {
    export enum status {
        PENDING = 'pending',
        RUNNING = 'running',
        COMPLETED = 'completed',
        FAILED = 'failed',
        SKIPPED = 'skipped',
    }
}

