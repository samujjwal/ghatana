/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type AiInsight = {
    id: string;
    title: string;
    description?: string;
    severity: AiInsight.severity;
    createdAt: string;
};
export namespace AiInsight {
    export enum severity {
        INFO = 'info',
        LOW = 'low',
        MEDIUM = 'medium',
        HIGH = 'high',
        CRITICAL = 'critical',
    }
}

