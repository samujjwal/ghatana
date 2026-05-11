/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AuthRole } from './AuthRole';
export type AuthUser = {
    id: string;
    email: string;
    name: string;
    firstName?: string;
    lastName?: string;
    role: AuthRole;
    avatar?: string;
    avatarUrl?: string;
    tenantId?: string;
    workspaceIds?: Array<string>;
    roles?: Array<string>;
};

