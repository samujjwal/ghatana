/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AuditEventRequest } from '../models/AuditEventRequest';
import type { AuditEventResponse } from '../models/AuditEventResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class AuditService {
    /**
     * Record a product audit event
     * @param requestBody
     * @returns AuditEventResponse Audit event recorded
     * @throws ApiError
     */
    public static recordAuditEvent(
        requestBody: AuditEventRequest,
    ): CancelablePromise<AuditEventResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/audit/events',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                401: `Authentication required or token invalid`,
            },
        });
    }
}
