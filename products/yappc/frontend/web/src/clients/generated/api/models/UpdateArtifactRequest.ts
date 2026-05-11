/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type UpdateArtifactRequest = {
    status?: UpdateArtifactRequest.status;
    content?: Record<string, any>;
};
export namespace UpdateArtifactRequest {
    export enum status {
        DRAFT = 'draft',
        COMPLETE = 'complete',
        APPROVED = 'approved',
    }
}

