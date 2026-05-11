/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type CreatePreviewSessionResponse = {
    /**
     * Unique session identifier
     */
    sessionId: string;
    /**
     * Signed token for the preview session
     */
    sessionToken: string;
    /**
     * Session expiration timestamp
     */
    expiresAt: string;
    /**
     * Session scope (projectId, artifactId, etc.)
     */
    scope: Record<string, any>;
};

