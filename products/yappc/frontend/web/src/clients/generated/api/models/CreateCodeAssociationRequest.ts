/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type CreateCodeAssociationRequest = {
    artifactId: string;
    filePath: string;
    lineStart?: number;
    lineEnd?: number;
    kind?: CreateCodeAssociationRequest.kind;
};
export namespace CreateCodeAssociationRequest {
    export enum kind {
        IMPLEMENTS = 'implements',
        REFERENCES = 'references',
        TESTS = 'tests',
        GENERATES = 'generates',
    }
}

