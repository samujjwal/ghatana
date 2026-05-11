/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ArtifactGraphQueryRequest } from '../models/ArtifactGraphQueryRequest';
import type { ArtifactGraphRequest } from '../models/ArtifactGraphRequest';
import type { ImportReviewDecisionRequest } from '../models/ImportReviewDecisionRequest';
import type { ImportReviewDecisionResponse } from '../models/ImportReviewDecisionResponse';
import type { RegistryCandidatePromotionRequest } from '../models/RegistryCandidatePromotionRequest';
import type { RegistryCandidatePromotionResponse } from '../models/RegistryCandidatePromotionResponse';
import type { ResidualIslandReviewRequest } from '../models/ResidualIslandReviewRequest';
import type { ResidualIslandReviewResponse } from '../models/ResidualIslandReviewResponse';
import type { SourceImportJob } from '../models/SourceImportJob';
import type { SourceImportRequest } from '../models/SourceImportRequest';
import type { SourceImportResponse } from '../models/SourceImportResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ArtifactGraphService {
    /**
     * Start a governed source import job
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns SourceImportResponse Source import job created and ready for review
     * @throws ApiError
     */
    public static importSource(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: SourceImportRequest,
    ): CancelablePromise<SourceImportResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/artifact/import-source',
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Poll a governed source import job
     * @param jobId
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @returns any Source import job progress snapshot
     * @throws ApiError
     */
    public static getSourceImportJob(
        jobId: string,
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
    ): CancelablePromise<{
        job: SourceImportJob;
    }> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/yappc/artifact/import-source/{jobId}',
            path: {
                'jobId': jobId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            errors: {
                400: `Bad request — invalid or missing parameters`,
                403: `Permission denied`,
                404: `Resource not found`,
            },
        });
    }
    /**
     * Promote a residual island to a reviewed registry candidate
     * @param artifactId
     * @param residualIslandId
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns RegistryCandidatePromotionResponse Registry candidate queued for review and backed by audit evidence
     * @throws ApiError
     */
    public static promoteRegistryCandidate(
        artifactId: string,
        residualIslandId: string,
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: RegistryCandidatePromotionRequest,
    ): CancelablePromise<RegistryCandidatePromotionResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/artifacts/{artifactId}/residual-islands/{residualIslandId}/registry-candidates',
            path: {
                'artifactId': artifactId,
                'residualIslandId': residualIslandId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                503: `Audit evidence could not be persisted, so the candidate was not accepted`,
            },
        });
    }
    /**
     * Persist a residual island review decision
     * @param artifactId
     * @param residualIslandId
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns ResidualIslandReviewResponse Residual island review decision persisted with audit evidence
     * @throws ApiError
     */
    public static persistResidualIslandReview(
        artifactId: string,
        residualIslandId: string,
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: ResidualIslandReviewRequest,
    ): CancelablePromise<ResidualIslandReviewResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/artifacts/{artifactId}/residual-islands/{residualIslandId}/review',
            path: {
                'artifactId': artifactId,
                'residualIslandId': residualIslandId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                503: `Audit evidence could not be persisted, so the decision was not accepted`,
            },
        });
    }
    /**
     * Persist an import review queue decision
     * @param artifactId
     * @param requestBody
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @returns ImportReviewDecisionResponse Import review decision persisted with audit evidence
     * @throws ApiError
     */
    public static persistImportReviewDecision(
        artifactId: string,
        requestBody: ImportReviewDecisionRequest,
        xTenantId?: string,
        xWorkspaceId?: string,
        xProjectId?: string,
    ): CancelablePromise<ImportReviewDecisionResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/artifacts/{artifactId}/import-review-decisions',
            path: {
                'artifactId': artifactId,
            },
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                503: `Audit evidence could not be persisted, so the decision was not accepted`,
            },
        });
    }
    /**
     * Ingest artifacts into the knowledge graph
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Ingest result
     * @throws ApiError
     */
    public static ingestArtifactGraph(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: ArtifactGraphRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/artifact/graph/ingest',
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Analyze the artifact knowledge graph
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Analysis result
     * @throws ApiError
     */
    public static analyzeArtifactGraph(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: ArtifactGraphRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/artifact/graph/analyze',
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Merge two artifact graphs
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Merge result
     * @throws ApiError
     */
    public static mergeArtifactGraph(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: ArtifactGraphRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/artifact/graph/merge',
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Query the artifact knowledge graph
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Query result
     * @throws ApiError
     */
    public static queryArtifactGraph(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: ArtifactGraphQueryRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/artifact/graph/query',
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Analyze residual artifacts not yet incorporated into the graph
     * @param xTenantId
     * @param xWorkspaceId
     * @param xProjectId
     * @param requestBody
     * @returns any Residual analysis result
     * @throws ApiError
     */
    public static analyzeResidual(
        xTenantId: string,
        xWorkspaceId: string,
        xProjectId: string,
        requestBody: ArtifactGraphRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/artifact/residual/analyze',
            headers: {
                'X-Tenant-ID': xTenantId,
                'X-Workspace-ID': xWorkspaceId,
                'X-Project-ID': xProjectId,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
