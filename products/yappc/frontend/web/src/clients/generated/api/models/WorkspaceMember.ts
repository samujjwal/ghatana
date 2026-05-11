/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type WorkspaceMember = {
    userId: string;
    workspaceId: string;
    role: WorkspaceMember.role;
    joinedAt?: string;
};
export namespace WorkspaceMember {
    export enum role {
        OWNER = 'owner',
        ADMIN = 'admin',
        MEMBER = 'member',
        VIEWER = 'viewer',
    }
}

