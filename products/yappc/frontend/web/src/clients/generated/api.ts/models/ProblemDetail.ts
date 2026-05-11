/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type ProblemDetail = {
    /**
     * A URI reference identifying the problem type (e.g. "https://yappc.ghatana.com/problems/not-found")
     */
    type: string;
    /**
     * Short human-readable summary of the problem type
     */
    title: string;
    /**
     * HTTP status code
     */
    status: number;
    /**
     * Human-readable explanation specific to this occurrence
     */
    detail?: string;
    /**
     * URI reference identifying the specific occurrence
     */
    instance?: string;
    /**
     * Request correlation ID for tracing
     */
    correlationId?: string;
    /**
     * Problem-type-specific extension members
     */
    extensions?: Record<string, any>;
};

