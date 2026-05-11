/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type CodeAssociation = {
    id: string;
    artifactId: string;
    filePath: string;
    lineStart?: number;
    lineEnd?: number;
    kind?: CodeAssociation.kind;
    createdAt: string;
};
export namespace CodeAssociation {
    export enum kind {
        IMPLEMENTS = 'implements',
        REFERENCES = 'references',
        TESTS = 'tests',
        GENERATES = 'generates',
    }
}

