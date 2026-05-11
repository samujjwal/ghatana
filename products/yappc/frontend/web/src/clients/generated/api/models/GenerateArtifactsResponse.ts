/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { GenerateDiffReview } from './GenerateDiffReview';
export type GenerateArtifactsResponse = {
    runId?: string;
    executionId?: string;
    status?: string;
    reviewRequired?: boolean;
    diff?: GenerateDiffReview;
};

