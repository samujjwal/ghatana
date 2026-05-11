/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type ExecuteProjectDashboardActionResponse = {
    projectId: string;
    actionId: string;
    outcome: ExecuteProjectDashboardActionResponse.outcome;
    targetPhase: string;
    targetPath: string;
    auditRecorded: boolean;
};
export namespace ExecuteProjectDashboardActionResponse {
    export enum outcome {
        OPENED_PHASE_COCKPIT = 'opened-phase-cockpit',
    }
}

