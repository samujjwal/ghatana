/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ArtifactEdgeRequest } from './ArtifactEdgeRequest';
import type { ArtifactNodeRequest } from './ArtifactNodeRequest';
import type { EdgeResolutionRecordRequest } from './EdgeResolutionRecordRequest';
import type { ResidualIslandRequest } from './ResidualIslandRequest';
import type { UnresolvedGraphEdgeRequest } from './UnresolvedGraphEdgeRequest';
export type ArtifactGraphIngestRequest = {
    projectId?: string;
    tenantId?: string;
    snapshotRef?: string;
    snapshotId?: string;
    versionId?: string;
    contentChecksum?: string;
    nodes?: Array<ArtifactNodeRequest>;
    edges?: Array<ArtifactEdgeRequest>;
    unresolvedEdges?: Array<UnresolvedGraphEdgeRequest>;
    edgeResolutionRecords?: Array<EdgeResolutionRecordRequest>;
    residualIslands?: Array<ResidualIslandRequest>;
};

