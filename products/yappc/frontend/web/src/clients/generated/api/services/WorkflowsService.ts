/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { WorkflowDefinition } from '../models/WorkflowDefinition';
import type { WorkflowStartResponse } from '../models/WorkflowStartResponse';
import type { WorkflowStatus } from '../models/WorkflowStatus';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class WorkflowsService {
    /**
     * List registered workflow templates
     * @returns string Template IDs
     * @throws ApiError
     */
    public static listWorkflowTemplates(): CancelablePromise<Array<string>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/workflows/templates',
        });
    }
    /**
     * Start a workflow run
     * @param templateId
     * @param xTenantId
     * @param requestBody
     * @returns WorkflowStartResponse Workflow started
     * @throws ApiError
     */
    public static startWorkflow(
        templateId: string,
        xTenantId: string,
        requestBody?: Record<string, any>,
    ): CancelablePromise<WorkflowStartResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/workflows/{templateId}/start',
            path: {
                'templateId': templateId,
            },
            headers: {
                'X-Tenant-Id': xTenantId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                404: `Unknown template`,
            },
        });
    }
    /**
     * Get workflow run status
     * @param runId
     * @returns WorkflowStatus Run status
     * @throws ApiError
     */
    public static getWorkflowStatus(
        runId: string,
    ): CancelablePromise<WorkflowStatus> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/workflows/{runId}/status',
            path: {
                'runId': runId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * List all workflows
     * @returns any Workflow list
     * @throws ApiError
     */
    public static listWorkflows(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/workflows',
        });
    }
    /**
     * Create a workflow
     * @param requestBody
     * @returns any Workflow created
     * @throws ApiError
     */
    public static createWorkflow(
        requestBody: WorkflowDefinition,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/workflows',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Get a workflow by ID
     * @param id
     * @returns any Workflow descriptor
     * @throws ApiError
     */
    public static getWorkflow(
        id: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/workflows/{id}',
            path: {
                'id': id,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Delete a workflow
     * @param id
     * @returns void
     * @throws ApiError
     */
    public static deleteWorkflow(
        id: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/api/v1/workflows/{id}',
            path: {
                'id': id,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Start a workflow run
     * @param id
     * @returns any Workflow run started
     * @throws ApiError
     */
    public static startWorkflow1(
        id: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/workflows/{id}/start',
            path: {
                'id': id,
            },
        });
    }
    /**
     * Pause a running workflow
     * @param id
     * @returns any Workflow paused
     * @throws ApiError
     */
    public static pauseWorkflow(
        id: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/workflows/{id}/pause',
            path: {
                'id': id,
            },
        });
    }
    /**
     * Resume a paused workflow
     * @param id
     * @returns any Workflow resumed
     * @throws ApiError
     */
    public static resumeWorkflow(
        id: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/workflows/{id}/resume',
            path: {
                'id': id,
            },
        });
    }
    /**
     * Cancel a workflow run
     * @param id
     * @returns any Workflow cancelled
     * @throws ApiError
     */
    public static cancelWorkflow(
        id: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/workflows/{id}/cancel',
            path: {
                'id': id,
            },
        });
    }
    /**
     * Advance workflow to the next step
     * @param id
     * @returns any Workflow advanced
     * @throws ApiError
     */
    public static advanceWorkflowStep(
        id: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/workflows/{id}/steps/advance',
            path: {
                'id': id,
            },
        });
    }
    /**
     * Jump to a specific step in the workflow
     * @param id
     * @param stepId
     * @returns any Jumped to step
     * @throws ApiError
     */
    public static gotoWorkflowStep(
        id: string,
        stepId: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/workflows/{id}/steps/{stepId}/goto',
            path: {
                'id': id,
                'stepId': stepId,
            },
        });
    }
    /**
     * Generate an execution plan for a workflow
     * @param id
     * @returns any Execution plan
     * @throws ApiError
     */
    public static generateWorkflowPlan(
        id: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/workflows/{id}/plans/generate',
            path: {
                'id': id,
            },
        });
    }
    /**
     * Approve a generated workflow plan
     * @param workflowId
     * @param planId
     * @returns any Plan approved
     * @throws ApiError
     */
    public static approveWorkflowPlan(
        workflowId: string,
        planId: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/workflows/{workflowId}/plans/{planId}/approve',
            path: {
                'workflowId': workflowId,
                'planId': planId,
            },
        });
    }
    /**
     * Reject a generated workflow plan
     * @param workflowId
     * @param planId
     * @returns any Plan rejected
     * @throws ApiError
     */
    public static rejectWorkflowPlan(
        workflowId: string,
        planId: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/workflows/{workflowId}/plans/{planId}/reject',
            path: {
                'workflowId': workflowId,
                'planId': planId,
            },
        });
    }
    /**
     * Update steps in a workflow plan
     * @param workflowId
     * @param planId
     * @param requestBody
     * @returns any Steps updated
     * @throws ApiError
     */
    public static updateWorkflowPlanSteps(
        workflowId: string,
        planId: string,
        requestBody: Record<string, any>,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/api/v1/workflows/{workflowId}/plans/{planId}/steps',
            path: {
                'workflowId': workflowId,
                'planId': planId,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Route a workflow to an appropriate execution engine
     * @param id
     * @returns any Routing result
     * @throws ApiError
     */
    public static routeWorkflow(
        id: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/workflows/{id}/route',
            path: {
                'id': id,
            },
        });
    }
}
