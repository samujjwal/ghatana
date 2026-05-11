/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type Artifact = {
    id: string;
    projectId: string;
    phase: string;
    type: string;
    status: Artifact.status;
    content?: Record<string, any>;
    createdAt?: string;
};
export namespace Artifact {
    export enum status {
        DRAFT = 'draft',
        COMPLETE = 'complete',
        APPROVED = 'approved',
    }
}

