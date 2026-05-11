/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type Suggestion = {
    id?: string;
    projectId?: string;
    phase?: string;
    type?: Suggestion.type;
    text?: string;
    generatedAt?: string;
};
export namespace Suggestion {
    export enum type {
        REQUIREMENT = 'REQUIREMENT',
        DESIGN = 'DESIGN',
        TEST = 'TEST',
        RISK = 'RISK',
        ACTION = 'ACTION',
    }
}

