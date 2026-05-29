/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { PreviewTokenScope } from './PreviewTokenScope';
export type PreviewSecurity = {
    trustLevel: string;
    tokenScopes: Array<PreviewTokenScope>;
    expiresAt?: string | null;
    expired: boolean;
    safe: boolean;
    issues: Array<string>;
};

