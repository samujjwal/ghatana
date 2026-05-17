/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ArtifactSourceLocation } from './ArtifactSourceLocation';
export type UnresolvedGraphEdgeRequest = {
    id?: string;
    sourceNodeId: string;
    targetRef: string;
    relationshipType: string;
    targetKindHint?: string;
    sourceLocation?: ArtifactSourceLocation;
    confidence?: number;
    metadata?: Record<string, any>;
    tenantId?: string;
    projectId?: string;
    workspaceId?: string;
};

