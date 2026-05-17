/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AdvancePhaseRequest } from '../models/AdvancePhaseRequest';
import type { Artifact } from '../models/Artifact';
import type { CreateArtifactRequest } from '../models/CreateArtifactRequest';
import type { DashboardActionRequest } from '../models/DashboardActionRequest';
import type { ExecutionPhaseResult } from '../models/ExecutionPhaseResult';
import type { ExecutionResult } from '../models/ExecutionResult';
import type { FullLifecycleRequest } from '../models/FullLifecycleRequest';
import type { GateValidationResult } from '../models/GateValidationResult';
import type { Persona } from '../models/Persona';
import type { Phase } from '../models/Phase';
import type { PhaseCockpitPacket } from '../models/PhaseCockpitPacket';
import type { PhaseList } from '../models/PhaseList';
import type { PhasePacketRequest } from '../models/PhasePacketRequest';
import type { PhaseTransitionResult } from '../models/PhaseTransitionResult';
import type { ProjectDashboardActions } from '../models/ProjectDashboardActions';
import type { ProjectPhaseStatus } from '../models/ProjectPhaseStatus';
import type { UpdateArtifactRequest } from '../models/UpdateArtifactRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class LifecycleService {
    /**
     * Service metadata
     * @returns any Service info
     * @throws ApiError
     */
    public static serviceInfo(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/yappc/info',
        });
    }
    /**
     * Get phase cockpit packet
     * Returns the canonical phase cockpit packet for a project and phase
     * @param phase Lifecycle phase (intent, shape, validate, generate, run, observe, learn, evolve)
     * @param projectId Project ID
     * @param workspaceId Workspace ID
     * @param correlationId Optional correlation ID for tracing
     * @returns PhaseCockpitPacket Phase cockpit packet
     * @throws ApiError
     */
    public static getPhasePacket(
        phase: 'intent' | 'shape' | 'validate' | 'generate' | 'run' | 'observe' | 'learn' | 'evolve',
        projectId: string,
        workspaceId?: string,
        correlationId?: string,
    ): CancelablePromise<PhaseCockpitPacket> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/phase/packet',
            query: {
                'phase': phase,
                'projectId': projectId,
                'workspaceId': workspaceId,
                'correlationId': correlationId,
            },
            errors: {
                400: `Bad request — invalid or missing parameters`,
                401: `Authentication required or token invalid`,
                403: `Permission denied`,
            },
        });
    }
    /**
     * Request phase cockpit packet
     * Request the canonical phase cockpit packet with full request body
     * @param requestBody
     * @returns PhaseCockpitPacket Phase cockpit packet
     * @throws ApiError
     */
    public static requestPhasePacket(
        requestBody: PhasePacketRequest,
    ): CancelablePromise<PhaseCockpitPacket> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/phase/packet',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                401: `Authentication required or token invalid`,
                403: `Permission denied`,
            },
        });
    }
    /**
     * Get dashboard actions
     * Returns backend-derived dashboard actions for a workspace
     * @param workspaceId Workspace ID
     * @param correlationId Optional correlation ID for tracing
     * @returns ProjectDashboardActions Dashboard actions
     * @throws ApiError
     */
    public static getDashboardActions(
        workspaceId: string,
        correlationId?: string,
    ): CancelablePromise<Array<ProjectDashboardActions>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/dashboard/actions',
            query: {
                'workspaceId': workspaceId,
                'correlationId': correlationId,
            },
            errors: {
                400: `Bad request — invalid or missing parameters`,
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * Request dashboard actions
     * Request backend-derived dashboard actions with full request body
     * @param requestBody
     * @returns ProjectDashboardActions Dashboard actions
     * @throws ApiError
     */
    public static requestDashboardActions(
        requestBody: DashboardActionRequest,
    ): CancelablePromise<Array<ProjectDashboardActions>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/dashboard/actions',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * List all lifecycle phases
     * @returns PhaseList Ordered list of phases
     * @throws ApiError
     */
    public static listPhases(): CancelablePromise<PhaseList> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/lifecycle/phases',
        });
    }
    /**
     * Advance a project to the next lifecycle phase
     * Runs all three gate checks (entry criteria, exit criteria, required artifacts).
     * Returns 200 if the gate passes, 409 if any blocker is present.
     *
     * @param requestBody
     * @returns PhaseTransitionResult Phase advanced successfully
     * @throws ApiError
     */
    public static advancePhase(
        requestBody: AdvancePhaseRequest,
    ): CancelablePromise<PhaseTransitionResult> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/lifecycle/advance',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                409: `Gate check failed — phase not advanced`,
            },
        });
    }
    /**
     * List lifecycle execution results
     * @returns ExecutionResult Execution results
     * @throws ApiError
     */
    public static listExecutionResults(): CancelablePromise<Array<ExecutionResult>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/results',
        });
    }
    /**
     * Get a lifecycle execution result
     * @param executionId
     * @returns ExecutionResult Execution result
     * @throws ApiError
     */
    public static getExecutionResult(
        executionId: string,
    ): CancelablePromise<ExecutionResult> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/results/{executionId}',
            path: {
                'executionId': executionId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Get the phase-level result for an execution
     * @param executionId
     * @returns ExecutionPhaseResult Phase result
     * @throws ApiError
     */
    public static getExecutionPhaseResult(
        executionId: string,
    ): CancelablePromise<ExecutionPhaseResult> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/results/{executionId}/phase',
            path: {
                'executionId': executionId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * List execution results for a project
     * @param projectId
     * @returns ExecutionResult Execution results
     * @throws ApiError
     */
    public static listProjectExecutionResults(
        projectId: string,
    ): CancelablePromise<Array<ExecutionResult>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/projects/{projectId}/results',
            path: {
                'projectId': projectId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * List lifecycle artifacts
     * @returns Artifact Artifacts
     * @throws ApiError
     */
    public static listArtifacts(): CancelablePromise<Array<Artifact>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/artifacts',
        });
    }
    /**
     * Create a lifecycle artifact
     * @param requestBody
     * @returns Artifact Artifact created
     * @throws ApiError
     */
    public static createArtifact(
        requestBody: CreateArtifactRequest,
    ): CancelablePromise<Artifact> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/artifacts',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Get a lifecycle artifact
     * @param artifactId
     * @returns Artifact Artifact
     * @throws ApiError
     */
    public static getArtifact(
        artifactId: string,
    ): CancelablePromise<Artifact> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/artifacts/{artifactId}',
            path: {
                'artifactId': artifactId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Update a lifecycle artifact
     * @param artifactId
     * @param requestBody
     * @returns Artifact Artifact updated
     * @throws ApiError
     */
    public static updateArtifact(
        artifactId: string,
        requestBody: UpdateArtifactRequest,
    ): CancelablePromise<Artifact> {
        return __request(OpenAPI, {
            method: 'PATCH',
            url: '/api/artifacts/{artifactId}',
            path: {
                'artifactId': artifactId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Delete a lifecycle artifact
     * @param artifactId
     * @returns void
     * @throws ApiError
     */
    public static deleteArtifact(
        artifactId: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/api/artifacts/{artifactId}',
            path: {
                'artifactId': artifactId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Validate whether phase gate requirements are met
     * @param requestBody
     * @returns GateValidationResult Gate validation result
     * @throws ApiError
     */
    public static validateGate(
        requestBody: {
            phase: string;
            projectId: string;
        },
    ): CancelablePromise<GateValidationResult> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/gates/validate',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * AI-derive personas from project artifacts
     * @param requestBody
     * @returns Persona Derived personas
     * @throws ApiError
     */
    public static derivePersonas(
        requestBody: {
            projectId: string;
        },
    ): CancelablePromise<Array<Persona>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/personas/derive',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * List all lifecycle phases
     * @returns Phase Phases
     * @throws ApiError
     */
    public static listPhases1(): CancelablePromise<Array<Phase>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/phases',
        });
    }
    /**
     * Get the next phase after the given phase
     * @param phase
     * @returns Phase Next phase
     * @throws ApiError
     */
    public static getNextPhase(
        phase: string,
    ): CancelablePromise<Phase> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/phases/{phase}/next',
            path: {
                'phase': phase,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Get the current lifecycle phase for a project
     * @param projectId
     * @returns ProjectPhaseStatus Current phase
     * @throws ApiError
     */
    public static getCurrentProjectPhase(
        projectId: string,
    ): CancelablePromise<ProjectPhaseStatus> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/projects/{projectId}/current',
            path: {
                'projectId': projectId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Execute the full YAPPC lifecycle pipeline (intent → shape → generate → validate → run)
     * @param requestBody
     * @returns any Full lifecycle execution result
     * @throws ApiError
     */
    public static executeLifecycle(
        requestBody: FullLifecycleRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/lifecycle/execute',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Evaluate AI phase gate readiness
     * @param requestBody
     * @returns any Readiness evaluation
     * @throws ApiError
     */
    public static evaluatePhaseGateReadiness(
        requestBody: Record<string, any>,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/ai/phase-gate-readiness',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
