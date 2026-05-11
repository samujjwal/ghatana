/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type CapabilityResponse = {
    userId: string;
    workspaceId?: string;
    projectId?: string;
    role?: CapabilityResponse.role;
    workspaceCapabilities: {
        canCreate?: boolean;
        canUpdate?: boolean;
        canDelete?: boolean;
    };
    projectCapabilities: {
        canCreate?: boolean;
        canUpdate?: boolean;
        canDelete?: boolean;
        canRead?: boolean;
    };
    lifecycleCapabilities: Array<{
        phase?: 'intent' | 'shape' | 'validate' | 'generate' | 'run' | 'observe' | 'learn' | 'evolve';
        canExecute?: boolean;
        canApprove?: boolean;
        canReject?: boolean;
    }>;
};
export namespace CapabilityResponse {
    export enum role {
        OWNER = 'OWNER',
        ADMIN = 'ADMIN',
        DEVELOPER = 'DEVELOPER',
        VIEWER = 'VIEWER',
    }
}

