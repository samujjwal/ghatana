/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type AuditEventResponse = {
    id: string;
    timestamp: string;
    type: string;
    userId: string;
    projectId: string;
    artifactId?: string;
    flowStage: string;
    phase: string;
    metadata?: Record<string, any>;
    description: string;
};

