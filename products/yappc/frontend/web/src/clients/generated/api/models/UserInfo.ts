/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AuthRole } from './AuthRole';
export type UserInfo = {
    id?: string;
    email?: string;
    name?: string;
    firstName?: string;
    lastName?: string;
    role?: AuthRole;
    tenantId?: string;
    workspaceIds?: Array<string>;
    avatar?: string;
    avatarUrl?: string;
    roles?: Array<string>;
};

