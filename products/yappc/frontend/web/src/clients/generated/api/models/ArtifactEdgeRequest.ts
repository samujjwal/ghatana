/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type ArtifactEdgeRequest = {
    edgeId: string;
    sourceNodeId: string;
    targetNodeId: string;
    relationshipType: string;
    properties?: Record<string, any>;
    confidence?: number;
    bidirectional?: boolean;
    metadata?: Record<string, any>;
    snapshotId?: string;
    versionId?: string;
};

