/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ArtifactSourceLocation } from './ArtifactSourceLocation';
export type ResidualIslandRequest = {
    id: string;
    islandType: string;
    summary?: string;
    originalSource: string;
    sourceLocation?: ArtifactSourceLocation;
    sourceSpan: string;
    checksum: string;
    rawFragmentRef: string;
    reason?: string;
    confidence?: number;
    reviewRequired?: boolean;
    riskScore?: number;
    metadata?: Record<string, string>;
    fileCount?: number;
    tenantId?: string;
    projectId?: string;
    workspaceId?: string;
    snapshotId?: string;
};

