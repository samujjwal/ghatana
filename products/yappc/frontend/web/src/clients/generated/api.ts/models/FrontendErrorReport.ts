/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type FrontendErrorReport = {
    message: string;
    stack?: string;
    componentName?: string;
    dataClassification?: FrontendErrorReport.dataClassification;
    tenantId?: string;
    userId?: string;
    url: string;
    userAgent: string;
};
export namespace FrontendErrorReport {
    export enum dataClassification {
        PUBLIC = 'PUBLIC',
        INTERNAL = 'INTERNAL',
        CONFIDENTIAL = 'CONFIDENTIAL',
        RESTRICTED = 'RESTRICTED',
        SENSITIVE = 'SENSITIVE',
        CREDENTIALS = 'CREDENTIALS',
        REGULATED = 'REGULATED',
    }
}

