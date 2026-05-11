/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type PhaseBlocker = {
    id: string;
    type: string;
    title: string;
    description: string;
    severity: PhaseBlocker.severity;
    resourceId: string;
    resolvable: boolean;
};
export namespace PhaseBlocker {
    export enum severity {
        LOW = 'LOW',
        MEDIUM = 'MEDIUM',
        HIGH = 'HIGH',
        CRITICAL = 'CRITICAL',
    }
}

