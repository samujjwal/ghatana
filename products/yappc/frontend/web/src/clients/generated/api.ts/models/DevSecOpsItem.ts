/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type DevSecOpsItem = {
    id: string;
    title: string;
    description?: string;
    severity: DevSecOpsItem.severity;
    status: DevSecOpsItem.status;
    createdAt?: string;
};
export namespace DevSecOpsItem {
    export enum severity {
        LOW = 'low',
        MEDIUM = 'medium',
        HIGH = 'high',
        CRITICAL = 'critical',
    }
    export enum status {
        OPEN = 'open',
        IN_PROGRESS = 'in-progress',
        RESOLVED = 'resolved',
        ACCEPTED = 'accepted',
    }
}

