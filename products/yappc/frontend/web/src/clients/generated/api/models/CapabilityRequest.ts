/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type CapabilityRequest = {
    /**
     * Tenant ID (optional, validated against principal)
     */
    tenantId?: string;
    /**
     * Workspace ID
     */
    workspaceId: string;
    /**
     * Project ID (optional)
     */
    projectId?: string;
    /**
     * Optional correlation ID for tracing
     */
    correlationId?: string;
};

