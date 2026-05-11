/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type ValidatePreviewSessionResponse = {
    /**
     * Whether the session token is valid
     */
    valid: boolean;
    /**
     * Session identifier if valid
     */
    sessionId: string;
    /**
     * Session expiration timestamp if valid
     */
    expiresAt?: string;
    /**
     * Session scope if valid
     */
    scope?: Record<string, any>;
    /**
     * Error message if validation failed
     */
    error?: string;
};

