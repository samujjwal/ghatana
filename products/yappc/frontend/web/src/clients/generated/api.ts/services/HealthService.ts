/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class HealthService {
    /**
     * Liveness probe
     * Returns 200 when the process is alive. Used by Kubernetes liveness checks.
     * @returns any Service is alive
     * @throws ApiError
     */
    public static liveness(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/health',
        });
    }
    /**
     * Readiness probe
     * Returns 200 when the service is ready to accept traffic. Includes a lightweight
     * DataCloud connectivity ping.
     *
     * @returns any Service is ready
     * @throws ApiError
     */
    public static readiness(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/ready',
            errors: {
                503: `Service not ready (dependency unavailable)`,
            },
        });
    }
    /**
     * Prometheus metrics scrape endpoint
     * Exposes all Micrometer metrics in Prometheus text format.
     * @returns string Prometheus metrics
     * @throws ApiError
     */
    public static scrapeMetrics(): CancelablePromise<string> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/metrics',
        });
    }
}
