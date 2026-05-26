/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type KernelProductUnitIntentRequest = {
    tenantId: string;
    workspaceId: string;
    projectId: string;
    projectName: string;
    surfaces: Array<string>;
    runtimeProvider?: string;
    lifecycleProfile?: string;
    sourcePhase?: string;
    metadata?: Record<string, any>;
    correlationId?: string;
};

