/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { SourceImportProgressStep } from './SourceImportProgressStep';
export type SourceImportJob = {
    id: string;
    status: SourceImportJob.status;
    reason?: string;
    tenantId?: string | null;
    workspaceId?: string | null;
    projectId?: string | null;
    sourceType?: string;
    source?: string;
    componentName?: string;
    percentComplete: number;
    currentStep: string;
    steps: Array<SourceImportProgressStep>;
    createdAt: string;
    updatedAt?: string;
    auditRecorded?: boolean;
};
export namespace SourceImportJob {
    export enum status {
        VALIDATING = 'VALIDATING',
        FETCHING_SOURCE = 'FETCHING_SOURCE',
        REVIEW_REQUIRED = 'REVIEW_REQUIRED',
        REJECTED = 'REJECTED',
        FAILED = 'FAILED',
    }
}

