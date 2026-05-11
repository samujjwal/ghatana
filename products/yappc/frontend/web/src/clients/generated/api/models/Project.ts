/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ProjectLifecyclePhase } from './ProjectLifecyclePhase';
import type { ProjectStatus } from './ProjectStatus';
import type { ProjectType } from './ProjectType';
export type Project = {
    id: string;
    name: string;
    description?: string;
    type: ProjectType;
    status: ProjectStatus;
    lifecyclePhase?: ProjectLifecyclePhase;
    ownerWorkspaceId: string;
    isDefault: boolean;
    aiSummary?: string;
    aiNextActions: Array<string>;
    aiHealthScore?: number;
    createdAt: string;
    updatedAt: string;
};

