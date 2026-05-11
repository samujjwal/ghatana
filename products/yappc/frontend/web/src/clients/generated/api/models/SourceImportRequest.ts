/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type SourceImportRequest = {
    sourceType: SourceImportRequest.sourceType;
    /**
     * HTTPS source URL or artifact reference.
     */
    source: string;
    projectId: string;
    targetComponentName?: string;
    options?: Record<string, any>;
};
export namespace SourceImportRequest {
    export enum sourceType {
        TSX = 'tsx',
        ROUTE = 'route',
        STORYBOOK = 'storybook',
        ARTIFACT = 'artifact',
        ZIP = 'zip',
    }
}

