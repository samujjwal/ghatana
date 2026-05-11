/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type ProjectDashboardActions = {
    projectId: string;
    projectName?: string;
    primaryAction: string;
    blockedActions: Array<string>;
    reviewRequiredActions: Array<string>;
    safeToContinueActions: Array<string>;
    reasonLabel: string;
    isDegraded: boolean;
    timestamp: number;
};

