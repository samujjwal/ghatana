/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type PhasePacketRequest = {
    phase: PhasePacketRequest.phase;
    /**
     * Tenant ID (optional, validated against principal)
     */
    tenantId?: string;
    projectId: string;
    /**
     * Workspace ID (optional)
     */
    workspaceId?: string;
    /**
     * Optional correlation ID for tracing
     */
    correlationId?: string;
};
export namespace PhasePacketRequest {
    export enum phase {
        INTENT = 'intent',
        SHAPE = 'shape',
        VALIDATE = 'validate',
        GENERATE = 'generate',
        RUN = 'run',
        OBSERVE = 'observe',
        LEARN = 'learn',
        EVOLVE = 'evolve',
    }
}

