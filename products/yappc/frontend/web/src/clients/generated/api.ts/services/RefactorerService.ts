/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { RefactorerJobRequest } from '../models/RefactorerJobRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class RefactorerService {
    /**
     * Create and submit a refactoring job
     * @param requestBody
     * @returns any Job accepted
     * @throws ApiError
     */
    public static createRefactorJob(
        requestBody: RefactorerJobRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/jobs',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Get job status
     * @param jobId
     * @returns any Job status
     * @throws ApiError
     */
    public static getRefactorJob(
        jobId: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/jobs/{jobId}',
            path: {
                'jobId': jobId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Cancel a refactoring job
     * @param jobId
     * @returns any Job cancelled
     * @throws ApiError
     */
    public static deleteRefactorJob(
        jobId: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/api/v1/jobs/{jobId}',
            path: {
                'jobId': jobId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Get the full report for a completed job
     * @param jobId
     * @returns any Job report (JSON or HTML based on Accept header)
     * @throws ApiError
     */
    public static getRefactorJobReport(
        jobId: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/jobs/{jobId}/report',
            path: {
                'jobId': jobId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Start a paused job (not yet implemented — returns 501)
     * @param jobId
     * @returns void
     * @throws ApiError
     */
    public static startRefactorJob(
        jobId: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/jobs/{jobId}/start',
            path: {
                'jobId': jobId,
            },
            errors: {
                501: `Not implemented`,
            },
        });
    }
    /**
     * Stop a running job (not yet implemented — returns 501)
     * @param jobId
     * @returns void
     * @throws ApiError
     */
    public static stopRefactorJob(
        jobId: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/jobs/{jobId}/stop',
            path: {
                'jobId': jobId,
            },
            errors: {
                501: `Not implemented`,
            },
        });
    }
    /**
     * Create a new run for an existing job
     * @param jobId
     * @param requestBody
     * @returns any Run accepted
     * @throws ApiError
     */
    public static createRefactorJobRun(
        jobId: string,
        requestBody: RefactorerJobRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/jobs/{jobId}/runs',
            path: {
                'jobId': jobId,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * List runs for a job
     * @param jobId
     * @returns any Run list
     * @throws ApiError
     */
    public static listRefactorJobRuns(
        jobId: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/jobs/{jobId}/runs',
            path: {
                'jobId': jobId,
            },
        });
    }
    /**
     * Get a specific run
     * @param jobId
     * @param runId
     * @returns any Run details
     * @throws ApiError
     */
    public static getRefactorJobRun(
        jobId: string,
        runId: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/jobs/{jobId}/runs/{runId}',
            path: {
                'jobId': jobId,
                'runId': runId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Stream logs for a specific job run
     * @param jobId
     * @param runId
     * @returns any Log stream (text/plain or text/event-stream)
     * @throws ApiError
     */
    public static getRefactorJobRunLogs(
        jobId: string,
        runId: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/jobs/{jobId}/runs/{runId}/logs',
            path: {
                'jobId': jobId,
                'runId': runId,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Diagnose repository or workspace issues
     * @param requestBody
     * @returns any Diagnosis result
     * @throws ApiError
     */
    public static diagnoseRefactorer(
        requestBody: Record<string, any>,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/v1/diagnose',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Get refactorer configuration
     * @returns any Configuration
     * @throws ApiError
     */
    public static getRefactorerConfig(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/v1/config',
        });
    }
}
