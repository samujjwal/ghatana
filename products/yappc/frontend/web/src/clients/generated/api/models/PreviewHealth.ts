/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type PreviewHealth = {
    isHealthy: boolean;
    status: string;
    issues: Array<string>;
    security?: {
        trustLevel: string;
        tokenScopes: Array<{
            id: string;
            name: string;
            required: boolean;
            granted: boolean;
        }>;
        expiresAt?: string | null;
        expired: boolean;
        safe: boolean;
        issues: Array<string>;
    };
};

