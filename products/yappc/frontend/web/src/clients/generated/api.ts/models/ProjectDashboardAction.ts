/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type ProjectDashboardAction = {
    id: string;
    projectId: string;
    projectName: string;
    workspaceId: string;
    lifecyclePhase: string;
    routePhase: string;
    kind: ProjectDashboardAction.kind;
    title: string;
    summary: string;
    severity: ProjectDashboardAction.severity;
    source: ProjectDashboardAction.source;
    requiresReview: boolean;
    safeToRun: boolean;
    updatedAt: string;
};
export namespace ProjectDashboardAction {
    export enum kind {
        BLOCKER = 'blocker',
        REVIEW = 'review',
        SAFE_TO_CONTINUE = 'safe-to-continue',
    }
    export enum severity {
        CRITICAL = 'critical',
        WARNING = 'warning',
        INFO = 'info',
    }
    export enum source {
        PROJECT_AI_NEXT_ACTIONS = 'project.aiNextActions',
        PROJECT_LIFECYCLE_PHASE = 'project.lifecyclePhase',
    }
}

