/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
/**
 * Authorization scope for the request (e.g., project:read, workspace:write, admin).
 *
 * Scope determines what permissions are required to access the resource.
 * Common scopes:
 * - workspace:read - read access to workspace resources
 * - workspace:write - write access to workspace resources
 * - project:read - read access to project resources
 * - project:write - write access to project resources
 * - admin - administrative access
 *
 * Backend extraction policy (priority order):
 * 1. Path parameters (e.g., /api/v1/projects/{projectId} extracts project:read)
 * 2. Query parameter (?scope=project:read)
 * 3. Header (X-Scope: project:read)
 *
 * For most routes, scope is optional and inferred from the session/credentials.
 * Explicit scope is required for cross-tenant or elevated operations.
 *
 */
export enum ScopeQuery {
    WORKSPACE_READ = 'workspace:read',
    WORKSPACE_WRITE = 'workspace:write',
    PROJECT_READ = 'project:read',
    PROJECT_WRITE = 'project:write',
    ADMIN = 'admin',
}
