/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type CreatePreviewSessionRequest = {
    /**
     * ID of the tenant (organization)
     */
    tenantId: string;
    /**
     * ID of the workspace
     */
    workspaceId: string;
    /**
     * ID of the project containing the artifact
     */
    projectId: string;
    /**
     * ID of the artifact to preview
     */
    artifactId: string;
    /**
     * ID of the user requesting the preview session
     */
    userId: string;
    /**
     * Session duration in seconds (max 86400)
     */
    duration?: number;
    /**
     * Additional scope constraints for the preview session
     */
    scope?: Record<string, any>;
};

