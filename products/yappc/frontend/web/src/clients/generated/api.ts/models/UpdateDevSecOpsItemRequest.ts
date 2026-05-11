/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type UpdateDevSecOpsItemRequest = {
    title?: string;
    description?: string;
    status?: UpdateDevSecOpsItemRequest.status;
    severity?: UpdateDevSecOpsItemRequest.severity;
};
export namespace UpdateDevSecOpsItemRequest {
    export enum status {
        OPEN = 'open',
        IN_PROGRESS = 'in-progress',
        RESOLVED = 'resolved',
        ACCEPTED = 'accepted',
    }
    export enum severity {
        LOW = 'low',
        MEDIUM = 'medium',
        HIGH = 'high',
        CRITICAL = 'critical',
    }
}

