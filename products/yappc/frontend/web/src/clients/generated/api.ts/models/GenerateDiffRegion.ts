/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { GenerateArtifactProvenance } from './GenerateArtifactProvenance';
export type GenerateDiffRegion = {
    id: string;
    type: GenerateDiffRegion.type;
    startLine: number;
    endLine: number;
    originalContent: string;
    newContent: string;
    owner: GenerateDiffRegion.owner;
    provenance: GenerateArtifactProvenance;
};
export namespace GenerateDiffRegion {
    export enum type {
        ADDITION = 'addition',
        DELETION = 'deletion',
        MODIFICATION = 'modification',
    }
    export enum owner {
        SYSTEM = 'system',
        USER = 'user',
    }
}

