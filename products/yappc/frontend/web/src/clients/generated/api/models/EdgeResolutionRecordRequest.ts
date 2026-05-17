/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type EdgeResolutionRecordRequest = {
    id?: string;
    unresolvedEdgeId: string;
    status: string;
    resolvedTargetId?: string;
    candidateIds?: Array<string>;
    reviewRequired?: boolean;
    resolutionMethod?: string;
    metadata?: Record<string, any>;
    tenantId?: string;
    projectId?: string;
    workspaceId?: string;
};

