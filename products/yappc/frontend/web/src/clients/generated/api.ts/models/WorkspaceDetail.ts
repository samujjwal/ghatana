/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { Project } from './Project';
import type { ProjectWithOwnership } from './ProjectWithOwnership';
import type { Workspace } from './Workspace';
export type WorkspaceDetail = (Workspace & {
    ownedProjects?: Array<Project>;
    includedProjects?: Array<ProjectWithOwnership>;
});

