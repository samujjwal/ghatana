/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type ErrorResponse = {
    /**
     * Machine-readable error code
     */
    error: ErrorResponse.error;
    /**
     * Human-readable error description
     */
    message: string;
    /**
     * HTTP status code
     */
    status: number;
    /**
     * Correlation ID for tracing
     */
    correlationId: string;
    /**
     * Additional error context (optional)
     */
    details?: Record<string, any> | null;
};
export namespace ErrorResponse {
    /**
     * Machine-readable error code
     */
    export enum error {
        BAD_REQUEST = 'BAD_REQUEST',
        UNAUTHORIZED = 'UNAUTHORIZED',
        FORBIDDEN = 'FORBIDDEN',
        NOT_FOUND = 'NOT_FOUND',
        CONFLICT = 'CONFLICT',
        UNPROCESSABLE_ENTITY = 'UNPROCESSABLE_ENTITY',
        LOCKED = 'LOCKED',
        RATE_LIMITED = 'RATE_LIMITED',
        INTERNAL_ERROR = 'INTERNAL_ERROR',
    }
}

