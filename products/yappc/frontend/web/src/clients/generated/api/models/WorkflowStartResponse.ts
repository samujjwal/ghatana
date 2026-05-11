/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type WorkflowStartResponse = {
    runId?: string;
    templateId?: string;
    status?: WorkflowStartResponse.status;
    startedAt?: string;
};
export namespace WorkflowStartResponse {
    export enum status {
        PENDING = 'PENDING',
        RUNNING = 'RUNNING',
    }
}

