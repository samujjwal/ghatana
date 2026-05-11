/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { FrontendErrorReport } from '../models/FrontendErrorReport';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class TelemetryService {
    /**
     * Report a frontend error for observability
     * @param requestBody
     * @returns any Error reported
     * @throws ApiError
     */
    public static reportFrontendError(
        requestBody: FrontendErrorReport,
    ): CancelablePromise<{
        accepted: boolean;
    }> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/telemetry/frontend-errors',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
}
