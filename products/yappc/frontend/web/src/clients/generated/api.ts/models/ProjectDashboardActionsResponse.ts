/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ProjectDashboardAction } from './ProjectDashboardAction';
export type ProjectDashboardActionsResponse = {
    workspaceId: string;
    blockedWork: Array<ProjectDashboardAction>;
    reviewRequired: Array<ProjectDashboardAction>;
    safeToContinue: Array<ProjectDashboardAction>;
    generatedAt: string;
};

