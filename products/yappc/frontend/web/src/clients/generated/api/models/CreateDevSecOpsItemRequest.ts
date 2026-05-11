/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type CreateDevSecOpsItemRequest = {
    title: string;
    description?: string;
    severity: CreateDevSecOpsItemRequest.severity;
};
export namespace CreateDevSecOpsItemRequest {
    export enum severity {
        LOW = 'low',
        MEDIUM = 'medium',
        HIGH = 'high',
        CRITICAL = 'critical',
    }
}

