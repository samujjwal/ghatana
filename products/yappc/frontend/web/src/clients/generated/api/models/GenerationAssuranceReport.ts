/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { GenerationAssuranceCheck } from './GenerationAssuranceCheck';
export type GenerationAssuranceReport = {
    /**
     * Overall pass/fail status of all assurance checks
     */
    passed: boolean;
    checks: Array<GenerationAssuranceCheck>;
};

