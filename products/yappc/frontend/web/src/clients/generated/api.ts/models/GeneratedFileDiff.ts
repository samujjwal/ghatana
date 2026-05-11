/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { GenerateArtifactProvenance } from './GenerateArtifactProvenance';
import type { GenerateDiffRegion } from './GenerateDiffRegion';
export type GeneratedFileDiff = {
    id: string;
    path: string;
    language: string;
    artifactType: GeneratedFileDiff.artifactType;
    provenance: GenerateArtifactProvenance;
    diffRegions: Array<GenerateDiffRegion>;
};
export namespace GeneratedFileDiff {
    export enum artifactType {
        SOURCE = 'source',
        TEST = 'test',
        CONFIG = 'config',
        DOCUMENTATION = 'documentation',
        SCHEMA = 'schema',
        API = 'api',
        INFRASTRUCTURE = 'infrastructure',
    }
}

