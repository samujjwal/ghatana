/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ArtifactSourceLocation } from './ArtifactSourceLocation';
export type ArtifactNodeRequest = {
    id: string;
    type: string;
    name?: string;
    filePath?: string;
    content?: string;
    properties?: Record<string, any>;
    tags?: Array<string>;
    tenantId?: string;
    projectId?: string;
    sourceLocation?: ArtifactSourceLocation;
    extractorId?: string;
    extractorVersion?: string;
    confidence?: number;
    provenance?: string;
    privacySecurityFlags?: Array<string>;
    residualFragmentIds?: Array<string>;
    sourceRef?: string;
    symbolRef?: string;
};

