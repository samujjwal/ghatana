/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { PreviewSecurity } from './PreviewSecurity';
export type PreviewHealth = {
    isHealthy: boolean;
    status: string;
    issues: Array<string>;
    security?: PreviewSecurity;
};

